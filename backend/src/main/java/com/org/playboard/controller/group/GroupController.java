package com.org.playboard.controller.group;

import com.org.playboard.dto.group.CreateGroupRequest;
import com.org.playboard.dto.group.CreateInviteRequest;
import com.org.playboard.dto.group.GroupListResponse;
import com.org.playboard.dto.group.GroupSummaryDto;
import com.org.playboard.dto.group.InviteResponse;
import com.org.playboard.dto.group.JoinGroupRequest;
import com.org.playboard.dto.group.MembersResponse;
import com.org.playboard.dto.group.RenameGroupRequest;
import com.org.playboard.service.group.GroupService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public GroupListResponse listGroups(@AuthenticationPrincipal UUID userId) {
        return groupService.listGroupsForUser(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupSummaryDto createGroup(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(userId, request);
    }

    @PostMapping("/join")
    public GroupSummaryDto joinGroup(@AuthenticationPrincipal UUID userId, @Valid @RequestBody JoinGroupRequest request) {
        return groupService.joinGroup(userId, request);
    }

    @PatchMapping("/{groupId}")
    public GroupSummaryDto renameGroup(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID groupId,
            @Valid @RequestBody RenameGroupRequest request) {
        return groupService.renameGroup(groupId, userId, request);
    }

    @PostMapping("/{groupId}/invites")
    public InviteResponse createInvite(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateInviteRequest request) {
        return groupService.createInvite(groupId, userId, request);
    }

    @GetMapping("/{groupId}/members")
    public MembersResponse listMembers(@AuthenticationPrincipal UUID userId, @PathVariable UUID groupId) {
        return groupService.listMembers(groupId, userId);
    }
}
