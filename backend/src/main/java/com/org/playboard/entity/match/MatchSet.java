package com.org.playboard.entity.match;

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
@Table(name = "match_sets")
public class MatchSet {

    @Id
    @GeneratedValue
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "set_no", nullable = false)
    private short setNo;

    @Column(name = "team1_score", nullable = false)
    private short team1Score;

    @Column(name = "team2_score", nullable = false)
    private short team2Score;

    public UUID getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public short getSetNo() {
        return setNo;
    }

    public void setSetNo(short setNo) {
        this.setNo = setNo;
    }

    public short getTeam1Score() {
        return team1Score;
    }

    public void setTeam1Score(short team1Score) {
        this.team1Score = team1Score;
    }

    public short getTeam2Score() {
        return team2Score;
    }

    public void setTeam2Score(short team2Score) {
        this.team2Score = team2Score;
    }
}
