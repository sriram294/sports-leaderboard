import Foundation

/// Loading phases that distinguish first load, content, empty, and retryable failure.
enum GroupLoadPhase: Equatable, Sendable {
    case loading
    case loaded
    case failed
}

/// Group sheet destinations owned by the feature view model.
enum GroupSheet: String, Identifiable, Equatable, Sendable {
    case createOrJoin
    case rename
    case invite
    case members
    case session

    var id: String { rawValue }
}

/// Create or join mode for the first-group and switcher flows.
enum GroupEntryMode: String, CaseIterable, Sendable {
    case create = "Create"
    case join = "Join"
}

/// Immutable presentation state for groups and the app shell.
struct GroupUiState: Equatable, Sendable {
    var phase: GroupLoadPhase = .loading
    var groups: [PlayGroup] = []
    var selectedGroupID: String?
    var presentedSheet: GroupSheet?
    var entryMode: GroupEntryMode = .create
    var groupName = ""
    var inviteCode = ""
    var memberEmail = ""
    var memberName = ""
    var sessionStart = ""
    var sessionEnd = ""
    var roster: GroupRoster?
    var invite: GroupInvite?
    var isSubmitting = false
    var isRosterLoading = false
    var errorMessage: String?
    var actionMessage: String?
    var pendingRemoval: GroupMember?

    var selectedGroup: PlayGroup? {
        groups.first { $0.id == selectedGroupID } ?? groups.first
    }

    var canSubmitEntry: Bool {
        !isSubmitting && (entryMode == .create ? !groupName.trimmed.isEmpty : !inviteCode.trimmed.isEmpty)
    }

    var canAddMember: Bool {
        !isSubmitting && memberEmail.contains("@") && !memberName.trimmed.isEmpty
    }


    var canSaveSession: Bool {
        guard !isSubmitting else { return false }
        if sessionStart.trimmed.isEmpty && sessionEnd.trimmed.isEmpty { return true }
        guard Self.isTime(sessionStart), Self.isTime(sessionEnd) else { return false }
        return sessionStart < sessionEnd
    }

    private static func isTime(_ value: String) -> Bool {
        let parts = value.split(separator: ":", omittingEmptySubsequences: false)
        guard parts.count == 2, parts[0].count == 2, parts[1].count == 2,
              let hour = Int(parts[0]), let minute = Int(parts[1]) else { return false }
        return (0...23).contains(hour) && (0...59).contains(minute)
    }
}

extension PlayGroup {
    /// Whether the caller can remove this roster member under backend role rules.
    func canRemove(_ member: GroupMember, currentUserID: String) -> Bool {
        guard canManage, member.role != .owner, member.role != .guest, member.userID != currentUserID else { return false }
        return myRole == .owner || member.role == .member
    }

    /// Whether the caller can promote or demote this member.
    func canChangeRole(of member: GroupMember, currentUserID: String) -> Bool {
        canChangeRoles && member.role != .owner && member.role != .guest && member.userID != currentUserID
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
