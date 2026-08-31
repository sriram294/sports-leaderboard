import Combine
import Foundation

/// Produces user-facing invite copy while keeping sharing content injectable.
protocol GroupInviteSharing: Sendable {
    func message(groupName: String, code: String) -> String
}

/// Production invite copy for the native share sheet.
struct PlayboardGroupInviteSharing: GroupInviteSharing {
    func message(groupName: String, code: String) -> String {
        "Join \"\(groupName)\" on Playboard. Use invite code \(code) in Groups → Join."
    }
}

/// Owns S02 group selection, mutations, permissions, and sheet state.
@MainActor
final class GroupViewModel: ObservableObject {
    @Published private(set) var state = GroupUiState()

    private let repository: any GroupRepository
    private let sharing: any GroupInviteSharing
    let currentUserID: String

    init(repository: any GroupRepository, currentUserID: String, sharing: any GroupInviteSharing = PlayboardGroupInviteSharing()) {
        self.repository = repository
        self.currentUserID = currentUserID
        self.sharing = sharing
    }

    var inviteShareMessage: String? {
        guard let group = state.selectedGroup, let code = state.invite?.code else { return nil }
        return sharing.message(groupName: group.name, code: code)
    }

    func load() async {
        state.phase = .loading
        state.errorMessage = nil
        do {
            let groups = try await repository.loadGroups()
            let savedID = await repository.selectedGroupID()
            state.groups = groups
            state.selectedGroupID = groups.contains { $0.id == savedID } ? savedID : groups.first?.id
            await repository.selectGroup(state.selectedGroupID)
            state.phase = .loaded
        } catch {
            state.phase = .failed
            state.errorMessage = message(for: error)
        }
    }

    func refresh() async {
        do {
            let groups = try await repository.loadGroups()
            state.groups = groups
            if !groups.contains(where: { $0.id == state.selectedGroupID }) {
                state.selectedGroupID = groups.first?.id
                await repository.selectGroup(state.selectedGroupID)
            }
            state.phase = .loaded
        } catch {
            if state.groups.isEmpty {
                state.phase = .failed
                state.errorMessage = message(for: error)
            } else {
                state.actionMessage = message(for: error)
            }
        }
    }

    func selectGroup(_ id: String) async {
        guard state.groups.contains(where: { $0.id == id }) else { return }
        state.selectedGroupID = id
        state.roster = nil
        await repository.selectGroup(id)
    }

    func presentEntry() {
        resetForm()
        state.presentedSheet = .createOrJoin
    }

    func presentRename() {
        guard let group = state.selectedGroup, group.canManage else { return denyAction() }
        state.groupName = group.name
        state.presentedSheet = .rename
    }

    func presentInvite() async {
        guard let group = state.selectedGroup, group.canManage else { return denyAction() }
        state.invite = nil
        state.presentedSheet = .invite
        state.isSubmitting = true
        do { state.invite = try await repository.createInvite(groupID: group.id) }
        catch { state.actionMessage = message(for: error) }
        state.isSubmitting = false
    }

    func presentMembers() async {
        guard state.selectedGroup != nil else { return }
        state.presentedSheet = .members
        await loadRoster()
    }

    func presentSession() {
        guard let group = state.selectedGroup, group.canManage else { return denyAction() }
        state.sessionStart = group.sessionStart ?? ""
        state.sessionEnd = group.sessionEnd ?? ""
        state.presentedSheet = .session
    }

    func dismissSheet() {
        state.presentedSheet = nil
        state.pendingRemoval = nil
        state.actionMessage = nil
        resetForm()
    }

    func setEntryMode(_ mode: GroupEntryMode) {
        state.entryMode = mode
        state.actionMessage = nil
    }

    func setGroupName(_ value: String) { state.groupName = value; state.actionMessage = nil }
    func setInviteCode(_ value: String) { state.inviteCode = value; state.actionMessage = nil }
    func setMemberEmail(_ value: String) { state.memberEmail = value; state.actionMessage = nil }
    func setMemberName(_ value: String) { state.memberName = value; state.actionMessage = nil }
    func setSessionStart(_ value: String) { state.sessionStart = value; state.actionMessage = nil }
    func setSessionEnd(_ value: String) { state.sessionEnd = value; state.actionMessage = nil }
    func clearActionMessage() { state.actionMessage = nil }

    func submitEntry() async {
        guard state.canSubmitEntry else { return }
        state.isSubmitting = true
        state.actionMessage = nil
        do {
            let group = if state.entryMode == .create {
                try await repository.createGroup(name: state.groupName)
            } else {
                try await repository.joinGroup(code: state.inviteCode)
            }
            await upsertAndSelect(group)
            dismissSheet()
        } catch {
            state.actionMessage = message(for: error)
        }
        state.isSubmitting = false
    }

    func submitRename() async {
        guard let group = state.selectedGroup, group.canManage, !state.groupName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        state.isSubmitting = true
        do {
            let updated = try await repository.renameGroup(id: group.id, name: state.groupName)
            replace(updated)
            dismissSheet()
        } catch { state.actionMessage = message(for: error) }
        state.isSubmitting = false
    }

    func loadRoster() async {
        guard let group = state.selectedGroup else { return }
        state.isRosterLoading = true
        state.actionMessage = nil
        do { state.roster = try await repository.loadRoster(groupID: group.id) }
        catch { state.actionMessage = message(for: error) }
        state.isRosterLoading = false
    }

    func addMember() async {
        guard state.canAddMember, let group = state.selectedGroup, group.canManage else { return }
        state.isSubmitting = true
        do {
            _ = try await repository.addMember(groupID: group.id, email: state.memberEmail, displayName: state.memberName)
            state.memberEmail = ""
            state.memberName = ""
            await loadRoster()
            await refresh()
        } catch { state.actionMessage = message(for: error) }
        state.isSubmitting = false
    }

    func submitSession() async {
        guard state.canSaveSession, let group = state.selectedGroup, group.canManage else { return }
        state.isSubmitting = true
        let start = state.sessionStart.trimmingCharacters(in: .whitespacesAndNewlines)
        let end = state.sessionEnd.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            let updated = try await repository.updateSession(
                groupID: group.id,
                start: start.isEmpty ? nil : start,
                end: end.isEmpty ? nil : end
            )
            replace(updated)
            dismissSheet()
        } catch { state.actionMessage = message(for: error) }
        state.isSubmitting = false
    }

    func requestRemoval(_ member: GroupMember) {
        guard let group = state.selectedGroup, group.canRemove(member, currentUserID: currentUserID) else { return denyAction() }
        state.pendingRemoval = member
    }

    func cancelRemoval() { state.pendingRemoval = nil }

    func confirmRemoval() async {
        guard let group = state.selectedGroup, let member = state.pendingRemoval else { return }
        state.pendingRemoval = nil
        do {
            try await repository.removeMember(groupID: group.id, userID: member.userID)
            await loadRoster()
            await refresh()
        } catch { state.actionMessage = message(for: error) }
    }

    func toggleRole(for member: GroupMember) async {
        guard let group = state.selectedGroup, group.canChangeRole(of: member, currentUserID: currentUserID) else { return denyAction() }
        let newRole: GroupRole = member.role == .admin ? .member : .admin
        do {
            _ = try await repository.changeRole(groupID: group.id, userID: member.userID, role: newRole)
            await loadRoster()
        } catch { state.actionMessage = message(for: error) }
    }

    private func upsertAndSelect(_ group: PlayGroup) async {
        replace(group, appendIfMissing: true)
        state.selectedGroupID = group.id
        await repository.selectGroup(group.id)
        state.phase = .loaded
    }

    private func replace(_ group: PlayGroup, appendIfMissing: Bool = false) {
        if let index = state.groups.firstIndex(where: { $0.id == group.id }) {
            state.groups[index] = group
        } else if appendIfMissing {
            state.groups.append(group)
        }
    }

    private func denyAction() { state.actionMessage = GroupRepositoryError.permissionDenied.message }

    private func resetForm() {
        state.groupName = ""
        state.inviteCode = ""
        state.memberEmail = ""
        state.memberName = ""
        state.sessionStart = ""
        state.sessionEnd = ""
        state.isSubmitting = false
    }

    private func message(for error: Error) -> String {
        (error as? GroupRepositoryError)?.message ?? GroupRepositoryError.invalidResponse.message
    }
}
