package com.org.playboard.entity.sport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sports")
public class Sport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Short id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "team_size", nullable = false)
    private Short teamSize;

    @Column(name = "points_per_set", nullable = false)
    private Short pointsPerSet;

    @Column(name = "win_by", nullable = false)
    private Short winBy;

    @Column(name = "sets_to_win", nullable = false)
    private Short setsToWin;

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Short getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(Short teamSize) {
        this.teamSize = teamSize;
    }

    public Short getPointsPerSet() {
        return pointsPerSet;
    }

    public void setPointsPerSet(Short pointsPerSet) {
        this.pointsPerSet = pointsPerSet;
    }

    public Short getWinBy() {
        return winBy;
    }

    public void setWinBy(Short winBy) {
        this.winBy = winBy;
    }

    public Short getSetsToWin() {
        return setsToWin;
    }

    public void setSetsToWin(Short setsToWin) {
        this.setsToWin = setsToWin;
    }
}
