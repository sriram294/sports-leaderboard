package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.org.playboard.dto.group.CreateGroupRequest;
import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.dto.stats.MonthlyFinishDto;
import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.repository.user.UserRepository;
import com.org.playboard.service.group.GroupService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MonthlyFinishIntegrationTest {
    @Autowired private MonthlyStandingsWriter writer;
    @Autowired private StatsQueryService statsQueryService;
    @Autowired private GroupService groupService;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void profileReturnsOnlyTheLatestTwelveCapturedMonthsWithGaps() {
        User owner = userRepository.save(user("owner"));
        User other = userRepository.save(user("other"));
        UUID groupId = groupService.createGroup(
                owner.getId(), new CreateGroupRequest("Finish History", "badminton_doubles")).id();
        Group group = groupRepository.findById(groupId).orElseThrow();
        GroupMember membership = new GroupMember();
        membership.setGroup(group);
        membership.setUser(other);
        membership.setRole(GroupRole.MEMBER);
        membership.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(membership);

        // This simulates a V12-era trophy. The V14 default is false, so it never leaks into
        // the graph even though it belongs to the same group and player.
        jdbc.update("""
                insert into monthly_trophy
                    (id, group_id, user_id, month, rating, games_played, wins, created_at, updated_at)
                values (?, ?, ?, date '2024-12-01', 50.0, 5, 3, now(), now())
                """, UUID.randomUUID(), groupId, owner.getId());

        YearMonth first = YearMonth.of(2025, 1);
        for (int index = 0; index < 13; index++) {
            YearMonth month = first.plusMonths(index);
            LeaderboardEntryDto otherEntry = entry(1, other, false);
            List<LeaderboardEntryDto> entries;
            if (index == 2) {
                entries = List.of(otherEntry); // owner did not play
            } else if (index == 3) {
                entries = List.of(otherEntry, entry(2, owner, true)); // provisional owner
            } else {
                entries = List.of(entry(1, owner, false), entry(2, other, false));
            }
            writer.capture(groupId, month, new LeaderboardRanker.Standings(entries, 2), Optional.of(entries.getFirst()));
        }

        List<MonthlyFinishDto> finishes = statsQueryService
                .getPlayerStats(groupId, owner.getId(), owner.getId())
                .monthlyFinishes();

        assertThat(finishes).hasSize(12);
        assertThat(finishes).extracting(MonthlyFinishDto::month)
                .containsExactly("2025-02", "2025-03", "2025-04", "2025-05", "2025-06", "2025-07",
                        "2025-08", "2025-09", "2025-10", "2025-11", "2025-12", "2026-01");
        assertThat(finishes.get(1).rank()).as("month without play").isNull();
        assertThat(finishes.get(2).rank()).as("provisional month").isNull();
        assertThat(finishes.get(2).qualifiedPlayers()).isEqualTo(1);
    }

    private static LeaderboardEntryDto entry(int rank, User user, boolean provisional) {
        return new LeaderboardEntryDto(
                rank, user.getId(), user.getDisplayName(), null, null, user.getAvatarColor(),
                provisional ? 1 : 5, provisional ? 1 : 3, provisional ? 0 : 2,
                100, 80, new BigDecimal("0.6000"), 0, 0,
                new BigDecimal("42.0"), provisional, List.of());
    }

    private static User user(String name) {
        User user = new User();
        user.setEmail("finish-" + name + "-" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + "@example.com");
        user.setDisplayName(name);
        user.setAvatarColor("#7ED321");
        return user;
    }
}
