package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.match.Match;
import com.org.playboard.entity.match.MatchParticipant;
import com.org.playboard.entity.match.MatchTeam;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.match.MatchParticipantRepository;
import com.org.playboard.repository.match.MatchRepository;
import com.org.playboard.repository.match.MatchTeamRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchRatingChangeServiceTest {

    @Test
    void wilsonFallbackUsesDisplayedBeforeAndAfterRatingsAndBulkRosterQueries() {
        GroupMemberRepository members = mock(GroupMemberRepository.class);
        MatchRepository matches = mock(MatchRepository.class);
        MatchTeamRepository teams = mock(MatchTeamRepository.class);
        MatchParticipantRepository participants = mock(MatchParticipantRepository.class);
        MatchRatingChangeService service =
                new MatchRatingChangeService(members, matches, teams, participants, false);

        UUID groupId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();
        UUID winningTeamId = UUID.randomUUID();
        UUID losingTeamId = UUID.randomUUID();
        Instant playedAt = Instant.parse("2026-08-01T10:00:00Z");

        Group group = mock(Group.class);
        Match match = mock(Match.class);
        when(group.getId()).thenReturn(groupId);
        when(match.getGroup()).thenReturn(group);
        when(match.getId()).thenReturn(matchId);
        when(match.getPlayedAt()).thenReturn(playedAt);

        MatchTeam winningTeam = team(match, winningTeamId, true);
        MatchTeam losingTeam = team(match, losingTeamId, false);
        MatchParticipant winner = participant(winningTeam, winnerId);
        MatchParticipant loser = participant(losingTeam, loserId);
        GroupMember winningMember = member(winnerId, GroupRole.MEMBER);
        GroupMember losingMember = member(loserId, GroupRole.MEMBER);
        when(members.findByGroupId(groupId)).thenReturn(List.of(winningMember, losingMember));
        when(matches.findRatingMatchesThrough(groupId, playedAt, matchId)).thenReturn(List.of(match));
        when(teams.findByMatchIdInOrderByMatchIdAscTeamNoAsc(List.of(matchId)))
                .thenReturn(List.of(winningTeam, losingTeam));
        when(participants.findByMatchTeamIdIn(List.of(winningTeamId, losingTeamId)))
                .thenReturn(List.of(winner, loser));

        var changes = service.calculate(match);

        assertThat(changes).extracting("ratingDelta")
                .containsExactly(new BigDecimal("20.65"), new BigDecimal("0.00"));
        verify(teams).findByMatchIdInOrderByMatchIdAscTeamNoAsc(List.of(matchId));
        verify(participants).findByMatchTeamIdIn(List.of(winningTeamId, losingTeamId));
    }

    private MatchTeam team(Match match, UUID id, boolean winner) {
        MatchTeam team = mock(MatchTeam.class);
        when(team.getId()).thenReturn(id);
        when(team.getMatch()).thenReturn(match);
        when(team.isWinner()).thenReturn(winner);
        return team;
    }

    private MatchParticipant participant(MatchTeam team, UUID userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        MatchParticipant participant = mock(MatchParticipant.class);
        when(participant.getMatchTeam()).thenReturn(team);
        when(participant.getUser()).thenReturn(user);
        return participant;
    }

    private GroupMember member(UUID userId, GroupRole role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        GroupMember member = mock(GroupMember.class);
        when(member.getUser()).thenReturn(user);
        when(member.getRole()).thenReturn(role);
        return member;
    }
}
