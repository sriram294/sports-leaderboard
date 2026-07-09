package com.org.playboard.service.group;

import com.org.playboard.common.ApiException;
import com.org.playboard.common.AvatarColorPicker;
import com.org.playboard.dto.group.CreateGroupRequest;
import com.org.playboard.dto.group.CreateInviteRequest;
import com.org.playboard.dto.group.GroupListResponse;
import com.org.playboard.dto.group.GroupSummaryDto;
import com.org.playboard.dto.group.InviteResponse;
import com.org.playboard.dto.group.JoinGroupRequest;
import com.org.playboard.dto.group.MemberDto;
import com.org.playboard.dto.group.MembersResponse;
import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupInvite;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.sport.Sport;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupInviteRepository;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.repository.match.MatchRepository;
import com.org.playboard.repository.sport.SportRepository;
import com.org.playboard.repository.user.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    // Excludes visually ambiguous characters (0/O, 1/I) since invite codes
    // are meant to be typed by hand, not just deep-linked.
    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 5;

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final SportRepository sportRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final GroupMembershipGuard membershipGuard;
    private final SecureRandom random = new SecureRandom();

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInviteRepository groupInviteRepository,
            SportRepository sportRepository,
            UserRepository userRepository,
            MatchRepository matchRepository,
            GroupMembershipGuard membershipGuard) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInviteRepository = groupInviteRepository;
        this.sportRepository = sportRepository;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.membershipGuard = membershipGuard;
    }

    @Transactional(readOnly = true)
    public GroupListResponse listGroupsForUser(UUID userId) {
        List<GroupSummaryDto> groups = groupMemberRepository
                .findByUserIdAndStatus(userId, MemberStatus.ACTIVE)
                .stream()
                .map(membership -> toSummary(membership.getGroup(), membership.getRole()))
                .toList();
        return new GroupListResponse(groups);
    }

    @Transactional
    public GroupSummaryDto createGroup(UUID userId, CreateGroupRequest request) {
        Sport sport = sportRepository
                .findByCode(request.sportCode())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPORT_NOT_FOUND", "Unknown sport code"));
        User creator = userRepository.getReferenceById(userId);

        Group group = new Group();
        group.setSport(sport);
        group.setName(request.name());
        group.setCreatedBy(creator);
        group.setAvatarColor(AvatarColorPicker.pick(request.name()));
        group = groupRepository.save(group);

        GroupMember owner = new GroupMember();
        owner.setGroup(group);
        owner.setUser(creator);
        owner.setRole(GroupRole.OWNER);
        owner.setStatus(MemberStatus.ACTIVE);
        groupMemberRepository.save(owner);

        return toSummary(group, GroupRole.OWNER);
    }

    @Transactional
    public GroupSummaryDto joinGroup(UUID userId, JoinGroupRequest request) {
        GroupInvite invite = groupInviteRepository
                .findByCode(request.code())
                .filter(this::isUsable)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "GROUP_INVITE_INVALID", "Invite code is invalid, expired, or exhausted"));

        Group group = invite.getGroup();
        Optional<GroupMember> existing = groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId);
        boolean alreadyActive = existing.map(m -> m.getStatus() == MemberStatus.ACTIVE).orElse(false);

        GroupMember member = existing.orElseGet(() -> {
            GroupMember created = new GroupMember();
            created.setGroup(group);
            created.setUser(userRepository.getReferenceById(userId));
            created.setRole(GroupRole.MEMBER);
            return created;
        });
        member.setStatus(MemberStatus.ACTIVE);
        groupMemberRepository.save(member);

        // Idempotent for an already-active member — rejoining with a still-valid
        // code shouldn't burn down a limited-use invite's remaining capacity.
        if (!alreadyActive) {
            invite.setUsedCount(invite.getUsedCount() + 1);
        }

        return toSummary(group, member.getRole());
    }

    @Transactional
    public InviteResponse createInvite(UUID groupId, UUID callerId, CreateInviteRequest request) {
        GroupMember caller = membershipGuard.requireRole(groupId, callerId, Set.of(GroupRole.OWNER, GroupRole.ADMIN));

        GroupInvite invite = new GroupInvite();
        invite.setGroup(caller.getGroup());
        invite.setCreatedBy(caller.getUser());
        invite.setMaxUses(request.maxUses());
        invite.setExpiresAt(
                request.expiresInHours() != null
                        ? Instant.now().plusSeconds(request.expiresInHours() * 3600L)
                        : null);
        invite.setCode(generateUniqueInviteCode());
        invite = groupInviteRepository.save(invite);

        return new InviteResponse(invite.getCode(), invite.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public MembersResponse listMembers(UUID groupId, UUID callerId) {
        membershipGuard.requireActiveMember(groupId, callerId);
        List<MemberDto> members = groupMemberRepository
                .findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)
                .stream()
                .map(MemberDto::from)
                .toList();
        return new MembersResponse(members);
    }

    private boolean isUsable(GroupInvite invite) {
        Instant now = Instant.now();
        boolean notExpired = invite.getExpiresAt() == null || invite.getExpiresAt().isAfter(now);
        boolean hasCapacity = invite.getMaxUses() == null || invite.getUsedCount() < invite.getMaxUses();
        return notExpired && hasCapacity;
    }

    private GroupSummaryDto toSummary(Group group, GroupRole myRole) {
        long memberCount = groupMemberRepository.countByGroupIdAndStatus(group.getId(), MemberStatus.ACTIVE);
        long matchCount = matchRepository.countByGroupIdAndDeletedFalse(group.getId());
        return GroupSummaryDto.of(group, memberCount, matchCount, myRole);
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = randomInviteCode();
            if (groupInviteRepository.findByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique invite code after " + INVITE_CODE_MAX_ATTEMPTS + " attempts");
    }

    private String randomInviteCode() {
        StringBuilder code = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            code.append(INVITE_CODE_ALPHABET.charAt(random.nextInt(INVITE_CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
