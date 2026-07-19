package com.org.playboard.controller.match;

import com.org.playboard.dto.match.MatchDetailDto;
import com.org.playboard.dto.match.MatchListResponse;
import com.org.playboard.dto.match.RecordMatchRequest;
import com.org.playboard.service.match.MatchService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public MatchListResponse listMatches(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID groupId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "false") boolean mine) {
        return matchService.listMatches(groupId, userId, cursor, limit, mine);
    }

    @GetMapping("/{matchId}")
    public MatchDetailDto getMatch(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID groupId, @PathVariable UUID matchId) {
        return matchService.getMatchDetail(groupId, matchId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchDetailDto createMatch(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID groupId,
            @Valid @RequestBody RecordMatchRequest request) {
        return matchService.createMatch(groupId, userId, request);
    }

    @PatchMapping("/{matchId}")
    public MatchDetailDto updateMatch(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID groupId,
            @PathVariable UUID matchId,
            @Valid @RequestBody RecordMatchRequest request) {
        return matchService.updateMatch(groupId, matchId, userId, request);
    }

    @DeleteMapping("/{matchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMatch(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID groupId, @PathVariable UUID matchId) {
        matchService.deleteMatch(groupId, matchId, userId);
    }
}
