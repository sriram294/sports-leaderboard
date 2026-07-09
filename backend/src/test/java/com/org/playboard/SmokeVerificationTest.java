package com.org.playboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.match.Match;
import com.org.playboard.entity.match.MatchAction;
import com.org.playboard.entity.match.MatchEvent;
import com.org.playboard.repository.match.MatchEventRepository;
import com.org.playboard.entity.match.MatchParticipant;
import com.org.playboard.repository.match.MatchParticipantRepository;
import com.org.playboard.repository.match.MatchRepository;
import com.org.playboard.entity.match.MatchSet;
import com.org.playboard.repository.match.MatchSetRepository;
import com.org.playboard.entity.match.MatchTeam;
import com.org.playboard.repository.match.MatchTeamRepository;
import com.org.playboard.entity.sport.Sport;
import com.org.playboard.repository.sport.SportRepository;
import com.org.playboard.entity.stats.MemberStats;
import com.org.playboard.repository.stats.MemberStatsRepository;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// Requires a live Postgres reachable via DB_URL/DB_USERNAME/DB_PASSWORD (see
// application.yml) — no Testcontainers yet, so this won't run standalone in CI.
@SpringBootTest
@Transactional
class SmokeVerificationTest {

    @Autowired
    private SportRepository sportRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private MatchTeamRepository matchTeamRepository;
    @Autowired
    private MatchParticipantRepository matchParticipantRepository;
    @Autowired
    private MatchSetRepository matchSetRepository;
    @Autowired
    private MatchEventRepository matchEventRepository;
    @Autowired
    private MemberStatsRepository memberStatsRepository;

    @Test
    void writesAndReadsTheFullEntityGraph() {
        Sport sport = sportRepository.findByCode("badminton_doubles").orElseThrow();

        User raj = userRepository.save(newUser("raj@example.com", "Raj"));
        User dev = userRepository.save(newUser("dev@example.com", "Dev"));
        User marcus = userRepository.save(newUser("marcus@example.com", "Marcus"));
        User kiran = userRepository.save(newUser("kiran@example.com", "Kiran"));

        Group group = new Group();
        group.setSport(sport);
        group.setName("Saturday Smashers");
        group.setCreatedBy(raj);
        group.setAvatarColor("#7ED321");
        group = groupRepository.save(group);

        GroupMember owner = new GroupMember();
        owner.setGroup(group);
        owner.setUser(raj);
        owner.setRole(GroupRole.OWNER);
        owner.setStatus(MemberStatus.ACTIVE);
        groupMemberRepository.save(owner);

        Match match = new Match();
        match.setGroup(group);
        match.setPlayedAt(Instant.now());
        match.setRecordedBy(raj);
        match = matchRepository.save(match);

        MatchTeam team1 = new MatchTeam();
        team1.setMatch(match);
        team1.setTeamNo((short) 1);
        team1.setWinner(true);
        team1 = matchTeamRepository.save(team1);

        MatchTeam team2 = new MatchTeam();
        team2.setMatch(match);
        team2.setTeamNo((short) 2);
        team2.setWinner(false);
        team2 = matchTeamRepository.save(team2);

        matchParticipantRepository.save(participant(match, team1, raj));
        matchParticipantRepository.save(participant(match, team1, dev));
        matchParticipantRepository.save(participant(match, team2, marcus));
        matchParticipantRepository.save(participant(match, team2, kiran));

        MatchSet set1 = new MatchSet();
        set1.setMatch(match);
        set1.setSetNo((short) 1);
        set1.setTeam1Score((short) 21);
        set1.setTeam2Score((short) 12);
        matchSetRepository.save(set1);

        MatchEvent event = new MatchEvent();
        event.setMatch(match);
        event.setUser(raj);
        event.setAction(MatchAction.CREATED);
        event.setSnapshot("{\"note\":\"smoke test\"}");
        matchEventRepository.save(event);

        MemberStats stats = new MemberStats(group, raj);
        stats.setMatchesPlayed(1);
        stats.setWins(1);
        stats.setPointsFor(21);
        stats.setPointsAgainst(12);
        stats.setCurrentStreak(1);
        stats.setBestStreak(1);
        memberStatsRepository.save(stats);
        memberStatsRepository.flush();

        // Converters: enum persisted/read back correctly (lowercase in DB, enum in Java)
        GroupMember reloadedOwner = groupMemberRepository.findByGroupIdAndUserId(group.getId(), raj.getId())
                .orElseThrow();
        assertThat(reloadedOwner.getRole()).isEqualTo(GroupRole.OWNER);
        assertThat(reloadedOwner.getStatus()).isEqualTo(MemberStatus.ACTIVE);

        // Keyset pagination query
        List<Match> firstPage = matchRepository.findFirstPage(group.getId(),
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(firstPage).hasSize(1);

        // Composite-key entity + DB-generated win_rate column
        MemberStats reloadedStats = memberStatsRepository.findByGroupIdAndUserId(group.getId(), raj.getId())
                .orElseThrow();
        assertThat(reloadedStats.getWinRate()).isEqualByComparingTo("1.0000");

        // Leaderboard ordering query
        assertThat(memberStatsRepository.findLeaderboard(group.getId())).hasSize(1);

        // JSONB snapshot round-trip
        MatchEvent reloadedEvent = matchEventRepository.findByMatchIdOrderByCreatedAt(match.getId()).get(0);
        assertThat(reloadedEvent.getAction()).isEqualTo(MatchAction.CREATED);
        assertThat(reloadedEvent.getSnapshot()).contains("smoke test");
    }

    private static User newUser(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(name);
        user.setAvatarColor("#7ED321");
        return user;
    }

    private static MatchParticipant participant(Match match, MatchTeam team, User user) {
        MatchParticipant participant = new MatchParticipant();
        participant.setMatch(match);
        participant.setMatchTeam(team);
        participant.setUser(user);
        return participant;
    }
}
