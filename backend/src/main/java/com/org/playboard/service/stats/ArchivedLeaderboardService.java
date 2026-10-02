package com.org.playboard.service.stats;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.dto.stats.LeaderboardResponse;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.stats.MonthlyStandingRepository;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import com.org.playboard.service.group.GroupMembershipGuard;
import com.org.playboard.service.user.AvatarUrlResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the immutable standings captured for the latest three completed months. */
@Service
public class ArchivedLeaderboardService {
    private static final ZoneId MONTH_ZONE = ZoneId.of("Asia/Kolkata");
    private final MonthlyTrophyRepository trophies;
    private final MonthlyStandingRepository standings;
    private final GroupMemberRepository members;
    private final GroupMembershipGuard membershipGuard;
    private final AvatarUrlResolver avatarUrls;

    public ArchivedLeaderboardService(MonthlyTrophyRepository trophies, MonthlyStandingRepository standings,
            GroupMemberRepository members, GroupMembershipGuard membershipGuard, AvatarUrlResolver avatarUrls) {
        this.trophies = trophies;
        this.standings = standings;
        this.members = members;
        this.membershipGuard = membershipGuard;
        this.avatarUrls = avatarUrls;
    }

    @Transactional(readOnly = true)
    public List<String> months(UUID groupId, UUID callerId) {
        membershipGuard.requireActiveMember(groupId, callerId);
        LocalDate currentMonth = YearMonth.now(MONTH_ZONE).atDay(1);
        return trophies.findLatestCapturedMonths(groupId, currentMonth).stream()
                .map(month -> YearMonth.from(month).toString()).toList();
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse standings(UUID groupId, UUID callerId, YearMonth month) {
        membershipGuard.requireActiveMember(groupId, callerId);
        LocalDate monthDate = month.atDay(1);
        if (!trophies.findLatestCapturedMonths(groupId, YearMonth.now(MONTH_ZONE).atDay(1)).contains(monthDate)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MONTHLY_STANDINGS_NOT_FOUND",
                    "No captured leaderboard snapshot exists for that month");
        }
        var trophy = trophies.findByGroupIdAndMonth(groupId, monthDate).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "MONTHLY_STANDINGS_NOT_FOUND", "No captured leaderboard snapshot exists for that month"));
        Map<UUID, User> users = new HashMap<>();
        for (GroupMember member : members.findByGroupId(groupId)) users.put(member.getUser().getId(), member.getUser());
        List<LeaderboardEntryDto> rows = standings.findByGroupIdAndMonthOrderByRank(groupId, monthDate).stream()
                .filter(row -> users.containsKey(row.getUserId()))
                .map(row -> {
                    User user = users.get(row.getUserId());
                    int games = row.getGamesPlayed();
                    int wins = row.getWins();
                    BigDecimal winRate = games == 0 ? BigDecimal.ZERO
                            : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(games), 4, RoundingMode.HALF_UP);
                    return new LeaderboardEntryDto(row.getRank(), row.getUserId(), user.getDisplayName(),
                            avatarUrls.resolve(user.getPhotoUrl()), user.getAvatarId(), user.getAvatarColor(),
                            games, wins, games - wins, 0, 0, winRate, 0, 0, row.getRating(), row.isProvisional(),
                            List.of(), row.getAlgorithmVersion(), "window", 0, BigDecimal.ZERO,
                            row.isProvisional() ? "games" : null, false);
                }).toList();
        String algorithm = rows.stream().findFirst().map(LeaderboardEntryDto::algorithmVersion)
                .orElse(trophy.getAlgorithmVersion());
        return new LeaderboardResponse(rows, trophy.getMinGamesToRank(), algorithm, "window");
    }
}
