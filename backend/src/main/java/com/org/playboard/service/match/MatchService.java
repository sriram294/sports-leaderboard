package com.org.playboard.service.match;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.match.MatchDetailDto;
import com.org.playboard.dto.match.MatchEventDto;
import com.org.playboard.dto.match.MatchListResponse;
import com.org.playboard.dto.match.MatchSummaryDto;
import com.org.playboard.dto.match.PlayerRefDto;
import com.org.playboard.dto.match.RecordMatchRequest;
import com.org.playboard.dto.match.RecordMatchRequest.SetInput;
import com.org.playboard.dto.match.RecordMatchRequest.TeamInput;
import com.org.playboard.dto.match.RecordedByDto;
import com.org.playboard.dto.match.SetDto;
import com.org.playboard.dto.match.TeamDto;
import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.match.Match;
import com.org.playboard.entity.match.MatchAction;
import com.org.playboard.entity.match.MatchEvent;
import com.org.playboard.entity.match.MatchParticipant;
import com.org.playboard.entity.match.MatchSet;
import com.org.playboard.entity.match.MatchTeam;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.match.MatchEventRepository;
import com.org.playboard.repository.match.MatchParticipantRepository;
import com.org.playboard.repository.match.MatchRepository;
import com.org.playboard.repository.match.MatchSetRepository;
import com.org.playboard.repository.match.MatchTeamRepository;
import com.org.playboard.repository.user.UserRepository;
import com.org.playboard.service.group.GroupMembershipGuard;
import com.org.playboard.service.stats.StatsRecalculationService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

    private static final int DEFAULT_PAGE_LIMIT = 20;
    private static final int MAX_PAGE_LIMIT = 50;

    private final MatchRepository matchRepository;
    private final MatchTeamRepository matchTeamRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchSetRepository matchSetRepository;
    private final MatchEventRepository matchEventRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupMembershipGuard membershipGuard;
    private final StatsRecalculationService statsRecalculationService;

    public MatchService(
            MatchRepository matchRepository,
            MatchTeamRepository matchTeamRepository,
            MatchParticipantRepository matchParticipantRepository,
            MatchSetRepository matchSetRepository,
            MatchEventRepository matchEventRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            GroupMembershipGuard membershipGuard,
            StatsRecalculationService statsRecalculationService) {
        this.matchRepository = matchRepository;
        this.matchTeamRepository = matchTeamRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchSetRepository = matchSetRepository;
        this.matchEventRepository = matchEventRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.membershipGuard = membershipGuard;
        this.statsRecalculationService = statsRecalculationService;
    }

    @Transactional(readOnly = true)
    public MatchListResponse listMatches(UUID groupId, UUID callerId, String cursor, Integer limit) {
        membershipGuard.requireActiveMember(groupId, callerId);
        int effectiveLimit = clamp(limit == null ? DEFAULT_PAGE_LIMIT : limit, 1, MAX_PAGE_LIMIT);
        Pageable pageable = PageRequest.of(0, effectiveLimit + 1);

        List<Match> page;
        if (cursor == null || cursor.isBlank()) {
            page = matchRepository.findFirstPage(groupId, pageable);
        } else {
            DecodedCursor decoded = decodeCursor(cursor);
            page = matchRepository.findNextPage(groupId, decoded.playedAt(), decoded.id(), pageable);
        }

        boolean hasMore = page.size() > effectiveLimit;
        List<Match> items = hasMore ? page.subList(0, effectiveLimit) : page;
        List<MatchSummaryDto> matches = items.stream().map(this::toSummary).toList();
        String nextCursor = hasMore ? encodeCursor(items.get(items.size() - 1)) : null;
        return new MatchListResponse(matches, nextCursor);
    }

    @Transactional(readOnly = true)
    public MatchDetailDto getMatchDetail(UUID groupId, UUID matchId, UUID callerId) {
        membershipGuard.requireActiveMember(groupId, callerId);
        return toDetail(findMatch(groupId, matchId));
    }

    /**
     * For cross-service use (StatsQueryService's {@code recentMatches}) — the
     * caller is expected to have already checked group membership, so this
     * intentionally skips {@link GroupMembershipGuard} to avoid a redundant lookup.
     */
    @Transactional(readOnly = true)
    public List<MatchSummaryDto> findRecentMatches(UUID groupId, UUID userId, int limit) {
        return matchParticipantRepository
                .findRecentMatchesForPlayer(groupId, userId, PageRequest.of(0, limit))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public MatchDetailDto createMatch(UUID groupId, UUID callerId, RecordMatchRequest request) {
        GroupMember caller = membershipGuard.requireActiveMember(groupId, callerId);
        Group group = caller.getGroup();
        validateRequest(group, request);

        Match match = new Match();
        match.setGroup(group);
        match.setPlayedAt(request.playedAt());
        match.setRecordedBy(caller.getUser());
        match = matchRepository.save(match);

        Set<UUID> affectedPlayers = applyRoster(match, request);
        recordEvent(match, caller.getUser(), MatchAction.CREATED);
        statsRecalculationService.recompute(group, affectedPlayers);

        return toDetail(match);
    }

    @Transactional
    public MatchDetailDto updateMatch(UUID groupId, UUID matchId, UUID callerId, RecordMatchRequest request) {
        Match match = findMatch(groupId, matchId);
        GroupMember caller = membershipGuard.requireActiveMember(groupId, callerId);
        requireEditPermission(match, caller);

        Group group = match.getGroup();
        validateRequest(group, request);

        Set<UUID> oldPlayers = matchParticipantRepository.findByMatchId(match.getId()).stream()
                .map(p -> p.getUser().getId())
                .collect(Collectors.toSet());

        match.setPlayedAt(request.playedAt());
        matchParticipantRepository.deleteByMatchId(match.getId());
        matchTeamRepository.deleteByMatchId(match.getId());
        matchSetRepository.deleteByMatchId(match.getId());
        // Force the deletes to hit the DB now — Hibernate orders a single
        // flush's inserts before its deletes, so without this the re-created
        // MatchTeam rows below would collide with the (match_id, team_no)
        // unique constraint on the not-yet-physically-deleted old rows.
        matchParticipantRepository.flush();
        matchTeamRepository.flush();
        matchSetRepository.flush();

        Set<UUID> newPlayers = applyRoster(match, request);
        recordEvent(match, caller.getUser(), MatchAction.EDITED);

        Set<UUID> affectedPlayers = new HashSet<>(oldPlayers);
        affectedPlayers.addAll(newPlayers);
        statsRecalculationService.recompute(group, affectedPlayers);

        return toDetail(match);
    }

    @Transactional
    public void deleteMatch(UUID groupId, UUID matchId, UUID callerId) {
        Match match = findMatch(groupId, matchId);
        GroupMember caller = membershipGuard.requireActiveMember(groupId, callerId);
        requireEditPermission(match, caller);

        Set<UUID> affectedPlayers = matchParticipantRepository.findByMatchId(match.getId()).stream()
                .map(p -> p.getUser().getId())
                .collect(Collectors.toSet());

        match.setDeleted(true);
        match.setDeletedAt(Instant.now());
        match.setDeletedBy(caller.getUser());

        recordEvent(match, caller.getUser(), MatchAction.DELETED);
        statsRecalculationService.recompute(match.getGroup(), affectedPlayers);
    }

    private Match findMatch(UUID groupId, UUID matchId) {
        return matchRepository
                .findByIdAndGroupIdAndDeletedFalse(matchId, groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "Match not found"));
    }

    private void requireEditPermission(Match match, GroupMember caller) {
        boolean isRecorder = match.getRecordedBy().getId().equals(caller.getUser().getId());
        boolean isPrivileged = caller.getRole() == GroupRole.OWNER || caller.getRole() == GroupRole.ADMIN;
        if (!isRecorder && !isPrivileged) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "MATCH_EDIT_FORBIDDEN",
                    "Only the recorder or a group admin/owner can edit or delete this match");
        }
    }

    private void validateRequest(Group group, RecordMatchRequest request) {
        List<TeamInput> teams = request.teams();
        if (teams.size() != 2) {
            throw invalidTeams("Exactly 2 teams are required");
        }
        Set<Short> teamNumbers = teams.stream().map(TeamInput::teamNo).collect(Collectors.toSet());
        if (!teamNumbers.equals(Set.of((short) 1, (short) 2))) {
            throw invalidTeams("Team numbers must be exactly 1 and 2");
        }
        if (request.winningTeamNo() != 1 && request.winningTeamNo() != 2) {
            throw invalidTeams("winningTeamNo must be 1 or 2");
        }

        short expectedTeamSize = group.getSport().getTeamSize();
        Set<UUID> allPlayers = new HashSet<>();
        for (TeamInput team : teams) {
            if (team.playerIds().size() != expectedTeamSize) {
                throw invalidTeams("Each team must have exactly " + expectedTeamSize + " player(s)");
            }
            if (new HashSet<>(team.playerIds()).size() != team.playerIds().size()) {
                throw invalidTeams("A team cannot list the same player twice");
            }
            for (UUID playerId : team.playerIds()) {
                if (!allPlayers.add(playerId)) {
                    throw invalidTeams("A player cannot appear on both teams");
                }
            }
        }
        for (UUID playerId : allPlayers) {
            groupMemberRepository
                    .findByGroupIdAndUserId(group.getId(), playerId)
                    .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                    .orElseThrow(() -> invalidTeams("All players must be active members of the group"));
        }

        if (request.sets().isEmpty()) {
            throw invalidScores("At least one set is required");
        }
        for (SetInput set : request.sets()) {
            if (set.team1Score() < 0 || set.team2Score() < 0) {
                throw invalidScores("Set scores cannot be negative");
            }
        }
    }

    private Set<UUID> applyRoster(Match match, RecordMatchRequest request) {
        Map<Short, MatchTeam> teamsByNo = new HashMap<>();
        for (TeamInput teamInput : request.teams()) {
            MatchTeam team = new MatchTeam();
            team.setMatch(match);
            team.setTeamNo(teamInput.teamNo());
            team.setWinner(teamInput.teamNo() == request.winningTeamNo());
            team = matchTeamRepository.save(team);
            teamsByNo.put(teamInput.teamNo(), team);
        }

        Set<UUID> playerIds = new LinkedHashSet<>();
        for (TeamInput teamInput : request.teams()) {
            MatchTeam team = teamsByNo.get(teamInput.teamNo());
            for (UUID playerId : teamInput.playerIds()) {
                MatchParticipant participant = new MatchParticipant();
                participant.setMatch(match);
                participant.setMatchTeam(team);
                participant.setUser(userRepository.getReferenceById(playerId));
                matchParticipantRepository.save(participant);
                playerIds.add(playerId);
            }
        }

        for (SetInput setInput : request.sets()) {
            MatchSet set = new MatchSet();
            set.setMatch(match);
            set.setSetNo(setInput.setNo());
            set.setTeam1Score(setInput.team1Score());
            set.setTeam2Score(setInput.team2Score());
            matchSetRepository.save(set);
        }

        return playerIds;
    }

    private void recordEvent(Match match, User actor, MatchAction action) {
        MatchEvent event = new MatchEvent();
        event.setMatch(match);
        event.setUser(actor);
        event.setAction(action);
        matchEventRepository.save(event);
    }

    private MatchSummaryDto toSummary(Match match) {
        return new MatchSummaryDto(match.getId(), match.getPlayedAt(), buildTeams(match), buildSets(match));
    }

    private MatchDetailDto toDetail(Match match) {
        List<MatchEventDto> events = matchEventRepository.findByMatchIdOrderByCreatedAt(match.getId()).stream()
                .map(e -> new MatchEventDto(
                        e.getUser().getId(),
                        e.getUser().getDisplayName(),
                        e.getAction().name().toLowerCase(Locale.ROOT),
                        e.getCreatedAt()))
                .toList();
        RecordedByDto recordedBy =
                new RecordedByDto(match.getRecordedBy().getId(), match.getRecordedBy().getDisplayName());
        return new MatchDetailDto(
                match.getId(),
                match.getPlayedAt(),
                buildTeams(match),
                buildSets(match),
                recordedBy,
                match.getCreatedAt(),
                events);
    }

    private List<TeamDto> buildTeams(Match match) {
        return matchTeamRepository.findByMatchIdOrderByTeamNo(match.getId()).stream()
                .map(team -> {
                    List<PlayerRefDto> players = matchParticipantRepository.findByMatchTeamId(team.getId()).stream()
                            .map(p -> {
                                User user = p.getUser();
                                return new PlayerRefDto(
                                        user.getId(), user.getDisplayName(), user.getAvatarColor(), user.getPhotoUrl());
                            })
                            .toList();
                    return new TeamDto(team.getTeamNo(), team.isWinner(), players);
                })
                .toList();
    }

    private List<SetDto> buildSets(Match match) {
        return matchSetRepository.findByMatchIdOrderBySetNo(match.getId()).stream()
                .map(s -> new SetDto(s.getSetNo(), s.getTeam1Score(), s.getTeam2Score()))
                .toList();
    }

    private ApiException invalidTeams(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MATCH_INVALID_TEAMS", message);
    }

    private ApiException invalidScores(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MATCH_INVALID_SCORES", message);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String encodeCursor(Match match) {
        String raw = match.getPlayedAt().toString() + "|" + match.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private DecodedCursor decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new DecodedCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MATCH_CURSOR_INVALID", "Invalid pagination cursor");
        }
    }

    private record DecodedCursor(Instant playedAt, UUID id) {}
}
