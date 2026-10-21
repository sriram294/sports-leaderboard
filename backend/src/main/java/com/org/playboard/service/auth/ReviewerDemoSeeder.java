package com.org.playboard.service.auth;

import com.org.playboard.dto.group.AddMemberRequest;
import com.org.playboard.dto.group.CreateGroupRequest;
import com.org.playboard.dto.group.GroupSummaryDto;
import com.org.playboard.dto.group.MemberDto;
import com.org.playboard.dto.match.RecordMatchRequest;
import com.org.playboard.dto.match.RecordMatchRequest.SetInput;
import com.org.playboard.dto.match.RecordMatchRequest.TeamInput;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.service.group.GroupService;
import com.org.playboard.service.match.MatchService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives the store-review account something to explore: when the reviewer signs in
 * with no active group, a "Playboard Demo" group is created with three demo members
 * (plus the usual shared guests) and a few weeks of recorded doubles matches, so the
 * leaderboard, match history, and stats screens all have data. Goes through the
 * regular {@link GroupService}/{@link MatchService} paths, so stats are computed
 * exactly as for real groups. The demo members are provisional users that can never
 * sign in (their emails are on a reserved {@code .invalid} domain).
 */
@Service
public class ReviewerDemoSeeder {

    static final String GROUP_NAME = "Playboard Demo";
    private static final String SPORT_CODE = "badminton_doubles";
    private static final List<String> DEMO_PLAYERS = List.of("Arjun", "Meera", "Kabir");

    // {team1 partner index, team2 a, team2 b, winning team, set scores...}: indices are
    // into [reviewer, Arjun, Meera, Kabir]; team 1 is always the reviewer + partner.
    private static final int[][] MATCHES = {
        {1, 2, 3, 1, 21, 17},
        {2, 1, 3, 2, 18, 21},
        {3, 1, 2, 1, 21, 19},
        {1, 2, 3, 1, 21, 12, 19, 21, 21, 16},
        {2, 1, 3, 1, 22, 20},
        {3, 1, 2, 2, 15, 21},
        {1, 2, 3, 2, 21, 23, 17, 21},
        {2, 1, 3, 1, 21, 14},
        {3, 1, 2, 1, 21, 18, 21, 19},
        {1, 2, 3, 1, 21, 16},
        {2, 1, 3, 2, 19, 21},
        {3, 1, 2, 1, 21, 11},
    };

    private final GroupService groupService;
    private final MatchService matchService;
    private final GroupMemberRepository groupMemberRepository;

    public ReviewerDemoSeeder(
            GroupService groupService, MatchService matchService, GroupMemberRepository groupMemberRepository) {
        this.groupService = groupService;
        this.matchService = matchService;
        this.groupMemberRepository = groupMemberRepository;
    }

    /** Seeds the demo group unless the reviewer already belongs to an active group. */
    @Transactional
    public void ensureDemoGroup(UUID reviewerId) {
        if (!groupMemberRepository.findByUserIdAndStatus(reviewerId, MemberStatus.ACTIVE).isEmpty()) {
            return;
        }
        GroupSummaryDto group = groupService.createGroup(reviewerId, new CreateGroupRequest(GROUP_NAME, SPORT_CODE));

        List<UUID> players = new ArrayList<>();
        players.add(reviewerId);
        for (String name : DEMO_PLAYERS) {
            String email = "demo-" + name.toLowerCase() + "@reviewer.playboard.invalid";
            MemberDto member = groupService.addMemberByEmail(group.id(), reviewerId, new AddMemberRequest(email, name));
            players.add(member.userId());
        }

        // Oldest first, two or three days apart, ending yesterday evening.
        Instant latest = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(20));
        for (int i = 0; i < MATCHES.length; i++) {
            int[] m = MATCHES[i];
            Instant playedAt = latest.minus(Duration.ofDays(2L * (MATCHES.length - 1 - i)));
            List<SetInput> sets = new ArrayList<>();
            for (int s = 4; s < m.length; s += 2) {
                sets.add(new SetInput((short) (sets.size() + 1), (short) m[s], (short) m[s + 1]));
            }
            RecordMatchRequest request = new RecordMatchRequest(
                    playedAt,
                    List.of(
                            new TeamInput((short) 1, List.of(players.get(0), players.get(m[0]))),
                            new TeamInput((short) 2, List.of(players.get(m[1]), players.get(m[2])))),
                    sets,
                    (short) m[3]);
            matchService.createMatch(group.id(), reviewerId, request);
        }
    }
}
