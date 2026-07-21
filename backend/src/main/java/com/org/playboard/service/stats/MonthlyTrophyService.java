package com.org.playboard.service.stats;

import com.org.playboard.dto.stats.MonthlyTrophyDto;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.stats.MonthlyTrophy;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import com.org.playboard.service.group.GroupMembershipGuard;
import com.org.playboard.service.user.AvatarUrlResolver;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the monthly crowns {@link MonthlyTrophyJob} awards.
 *
 * <p>Read-only by design — nothing here decides a winner. Awarding happens once, in the job,
 * so that a trophy is a stored fact rather than something recomputed (and therefore able to
 * silently change) on every profile view.
 */
@Service
public class MonthlyTrophyService {

    private final MonthlyTrophyRepository trophyRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMembershipGuard membershipGuard;
    private final AvatarUrlResolver avatarUrls;

    public MonthlyTrophyService(
            MonthlyTrophyRepository trophyRepository,
            GroupMemberRepository groupMemberRepository,
            GroupMembershipGuard membershipGuard,
            AvatarUrlResolver avatarUrls) {
        this.trophyRepository = trophyRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.membershipGuard = membershipGuard;
        this.avatarUrls = avatarUrls;
    }

    /**
     * One player's crowns, newest first. Takes the already-resolved {@link User} because the
     * only caller has just loaded it — re-fetching per trophy would be a query per row for
     * identity that cannot differ between them.
     */
    @Transactional(readOnly = true)
    public List<MonthlyTrophyDto> forPlayer(UUID groupId, User user) {
        return trophyRepository.findByGroupIdAndUserIdOrderByMonthDesc(groupId, user.getId()).stream()
                .map(trophy -> toDto(trophy, user))
                .toList();
    }

    /**
     * The group's roll of honour, newest first, capped at {@code limit}.
     *
     * <p>Winners are resolved through the member list rather than by id: a player who has
     * since left the group still keeps the month they won, and their row must still render.
     * Any winner no longer resolvable is dropped rather than shown nameless.
     */
    @Transactional(readOnly = true)
    public List<MonthlyTrophyDto> forGroup(UUID groupId, UUID callerId, int limit) {
        membershipGuard.requireActiveMember(groupId, callerId);

        List<MonthlyTrophy> trophies = trophyRepository.findByGroupIdAndUserIdIsNotNullOrderByMonthDesc(groupId);
        if (trophies.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> winners = winnersById(groupId);

        return trophies.stream()
                .limit(limit)
                .filter(trophy -> winners.containsKey(trophy.getUserId()))
                .map(trophy -> toDto(trophy, winners.get(trophy.getUserId())))
                .toList();
    }

    /** Every user who has ever been a member of the group, active or not, keyed by id. */
    private Map<UUID, User> winnersById(UUID groupId) {
        Map<UUID, User> byId = new HashMap<>();
        for (GroupMember member : groupMemberRepository.findByGroupId(groupId)) {
            byId.put(member.getUser().getId(), member.getUser());
        }
        return byId;
    }

    private MonthlyTrophyDto toDto(MonthlyTrophy trophy, User user) {
        return new MonthlyTrophyDto(
                YearMonth.from(trophy.getMonth()).toString(),
                user.getId(),
                user.getDisplayName(),
                avatarUrls.resolve(user.getPhotoUrl()),
                user.getAvatarId(),
                user.getAvatarColor(),
                trophy.getRating(),
                trophy.getGamesPlayed(),
                trophy.getWins());
    }
}
