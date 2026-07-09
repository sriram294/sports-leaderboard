package com.org.playboard.entity.match;

import com.org.playboard.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "match_participants")
public class MatchParticipant {

    @Id
    @GeneratedValue
    @Column(name = "id")
    private UUID id;

    // Denormalized from matchTeam.match for a cheap (match_id, user_id) uniqueness
    // check and direct "player's matches" queries — see data-model.md.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_team_id", nullable = false)
    private MatchTeam matchTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public UUID getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public MatchTeam getMatchTeam() {
        return matchTeam;
    }

    public void setMatchTeam(MatchTeam matchTeam) {
        this.matchTeam = matchTeam;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
