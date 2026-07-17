package com.org.playboard.service.group;

import com.org.playboard.common.ApiException;
import com.org.playboard.common.AvatarColorPicker;
import com.org.playboard.common.DefaultAvatars;
import com.org.playboard.common.EmailNormalizer;
import com.org.playboard.dto.group.AddMemberRequest;
import com.org.playboard.dto.group.CreateGroupRequest;
import com.org.playboard.dto.group.CreateInviteRequest;
import com.org.playboard.dto.group.GroupListResponse;
import com.org.playboard.dto.group.GroupSummaryDto;
import com.org.playboard.dto.group.InviteResponse;
import com.org.playboard.dto.group.JoinGroupRequest;
import com.org.playboard.dto.group.MemberDto;
import com.org.playboard.dto.group.MembersResponse;
import com.org.playboard.dto.group.RenameGroupRequest;
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
import com.org.playboard.service.notification.events.MemberAddedEvent;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
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

    // Filler players for one-off non-members. Enough for "1 regular + 3 guests"
    // in a doubles match. Guests are a single global pool shared by every group
    // (they carry no stats/identity, so "Guest N" is identical everywhere) —
    // V5__shared_guest_fillers.sql seeds these users; keep the count/naming/color
    // and the email pattern below in sync with it.
    private static final int GUEST_FILLER_COUNT = 3;
    private static final String GUEST_AVATAR_COLOR = "#9AA0A6";

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final SportRepository sportRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final GroupMembershipGuard membershipGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInviteRepository groupInviteRepository,
            SportRepository sportRepository,
            UserRepository userRepository,
            MatchRepository matchRepository,
            GroupMembershipGuard membershipGuard,
            ApplicationEventPublisher eventPublisher) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInviteRepository = groupInviteRepository;
        this.sportRepository = sportRepository;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.membershipGuard = membershipGuard;
        this.eventPublisher = eventPublisher;
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

        seedGuestFillers(group);

        return toSummary(group, GroupRole.OWNER);
    }

    /**
     * Links this group to the shared pool of guest fillers (see {@link GroupRole#GUEST}).
     * Each guest is a global synthetic user reused by every group, so no new user
     * rows are created per group — only the per-group {@code group_members} rows
     * that make each guest an active member (required for match validation and to
     * keep guests out of counts/stats via their role).
     */
    private void seedGuestFillers(Group group) {
        for (int n = 1; n <= GUEST_FILLER_COUNT; n++) {
            GroupMember guest = new GroupMember();
            guest.setGroup(group);
            guest.setUser(globalGuestUser(n));
            guest.setRole(GroupRole.GUEST);
            guest.setStatus(MemberStatus.ACTIVE);
            groupMemberRepository.save(guest);
        }
    }

    /**
     * The shared guest user #{@code n} (1-based), normally seeded by
     * V5__shared_guest_fillers.sql. Defensively created here if absent (e.g. a
     * fresh DB whose first group is made before/without that seed).
     */
    private User globalGuestUser(int n) {
        String email = "guest-" + n + "@playboard.local";
        return userRepository.findByEmail(email).orElseGet(() -> {
            User guestUser = new User();
            guestUser.setDisplayName("Guest " + n);
            guestUser.setEmail(email);
            guestUser.setAvatarColor(GUEST_AVATAR_COLOR);
            return userRepository.save(guestUser);
        });
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

    /**
     * Adds a person to the group by email + name (owner/admin only). Onboards
     * users who can't sign in yet (e.g. no iOS app): a real {@link GroupRole#MEMBER}
     * is created so they show in the roster, are pickable for matches, and accrue
     * stats. If a user with that email already exists their row is reused (their
     * identity wins — the typed name is ignored); otherwise a provisional user is
     * created with {@code google_sub = null}, which is linked to their Google
     * account automatically on first sign-in (see
     * {@link com.org.playboard.service.auth.AuthService#signInWithGoogle}).
     */
    @Transactional
    public MemberDto addMemberByEmail(UUID groupId, UUID callerId, AddMemberRequest request) {
        GroupMember caller = membershipGuard.requireRole(groupId, callerId, Set.of(GroupRole.OWNER, GroupRole.ADMIN));
        Group group = caller.getGroup();

        String email = EmailNormalizer.normalize(request.email());
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User created = new User();
            created.setEmail(email);
            created.setDisplayName(request.displayName().trim());
            created.setAvatarColor(AvatarColorPicker.pick(email));
            created.setAvatarId(DefaultAvatars.pickRandom());
            // google_sub stays null — set when they first sign in with this email.
            return userRepository.save(created);
        });

        Optional<GroupMember> existing = groupMemberRepository.findByGroupIdAndUserId(group.getId(), user.getId());
        if (existing.map(m -> m.getStatus() == MemberStatus.ACTIVE).orElse(false)) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_MEMBER_EXISTS", "That person is already in this group");
        }

        // Reactivate a previously-removed member in place (keeping their old role),
        // else create a fresh MEMBER — mirrors joinGroup's upsert on (group, user).
        GroupMember member = existing.orElseGet(() -> {
            GroupMember created = new GroupMember();
            created.setGroup(group);
            created.setUser(user);
            created.setRole(GroupRole.MEMBER);
            return created;
        });
        member.setStatus(MemberStatus.ACTIVE);
        groupMemberRepository.save(member);

        eventPublisher.publishEvent(new MemberAddedEvent(group.getId(), group.getName(), user.getId()));

        return MemberDto.from(member);
    }

    /**
     * Renames a group. Owner/admin only (same gate as invites). The avatar color
     * is left untouched — it stays stable so the group looks the same everywhere
     * after a rename.
     */
    @Transactional
    public GroupSummaryDto renameGroup(UUID groupId, UUID callerId, RenameGroupRequest request) {
        GroupMember caller = membershipGuard.requireRole(groupId, callerId, Set.of(GroupRole.OWNER, GroupRole.ADMIN));
        Group group = caller.getGroup();
        group.setName(request.name().trim());
        group = groupRepository.save(group);
        return toSummary(group, caller.getRole());
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
        List<GroupMember> active = groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE);
        List<MemberDto> members = active.stream()
                .filter(m -> m.getRole() != GroupRole.GUEST)
                .map(MemberDto::from)
                .toList();
        // Guest fillers, ordered "Guest 1/2/3" for a stable picker order.
        List<MemberDto> guests = active.stream()
                .filter(m -> m.getRole() == GroupRole.GUEST)
                .map(MemberDto::from)
                .sorted(Comparator.comparing(MemberDto::displayName))
                .toList();
        return new MembersResponse(members, guests);
    }

    private boolean isUsable(GroupInvite invite) {
        Instant now = Instant.now();
        boolean notExpired = invite.getExpiresAt() == null || invite.getExpiresAt().isAfter(now);
        boolean hasCapacity = invite.getMaxUses() == null || invite.getUsedCount() < invite.getMaxUses();
        return notExpired && hasCapacity;
    }

    private GroupSummaryDto toSummary(Group group, GroupRole myRole) {
        // Guests are fillers, not players — they don't count toward member count.
        long memberCount = groupMemberRepository
                .countByGroupIdAndStatusAndRoleNot(group.getId(), MemberStatus.ACTIVE, GroupRole.GUEST);
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
