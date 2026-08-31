import Foundation
import Testing
@testable import Playboard

@MainActor
@Suite("Group view model")
struct GroupViewModelTests {
    @Test("Restores a valid selected group and falls back when stale")
    func restoresAndFallsBack() async {
        let repository = InMemoryGroupRepository(groups: [ownerGroup, memberGroup], selectedID: "missing")
        let viewModel = GroupViewModel(repository: repository, currentUserID: "me")

        await viewModel.load()

        #expect(viewModel.state.selectedGroupID == ownerGroup.id)
        #expect(await repository.selectedGroupID() == ownerGroup.id)
    }

    @Test("Permission matrix protects owners, self, admins, and guests")
    func permissionMatrix() {
        let owner = member(id: "owner", role: .owner)
        let admin = member(id: "admin", role: .admin)
        let regular = member(id: "member", role: .member)
        let guest = member(id: "guest", role: .guest)

        #expect(!ownerGroup.canRemove(owner, currentUserID: "me"))
        #expect(ownerGroup.canRemove(admin, currentUserID: "me"))
        #expect(ownerGroup.canChangeRole(of: regular, currentUserID: "me"))
        #expect(!ownerGroup.canChangeRole(of: guest, currentUserID: "me"))
        #expect(!memberGroup.canRemove(regular, currentUserID: "me"))
    }

    @Test("Creating a first group selects and persists it")
    func createsAndSelects() async {
        let repository = InMemoryGroupRepository(groups: [], selectedID: nil)
        let viewModel = GroupViewModel(repository: repository, currentUserID: "me")
        await viewModel.load()
        viewModel.presentEntry()
        viewModel.setGroupName("Court Crew")

        await viewModel.submitEntry()

        #expect(viewModel.state.selectedGroup?.name == "Court Crew")
        #expect(await repository.selectedGroupID() == "created")
    }

    private var ownerGroup: PlayGroup { group(id: "owner-group", role: .owner) }
    private var memberGroup: PlayGroup { group(id: "member-group", role: .member) }
    private func group(id: String, role: GroupRole) -> PlayGroup {
        PlayGroup(id: id, name: id, avatarColor: "#9ADE28", sportCode: "badminton_doubles", memberCount: 2, matchCount: 0, myRole: role, sessionStart: nil, sessionEnd: nil)
    }
    private func member(id: String, role: GroupRole) -> GroupMember {
        GroupMember(userID: id, displayName: id, photoURL: nil, avatarID: nil, avatarColor: "#9ADE28", role: role)
    }
}

private actor InMemoryGroupRepository: GroupRepository {
    private var groups: [PlayGroup]
    private var selectedID: String?
    init(groups: [PlayGroup], selectedID: String?) { self.groups = groups; self.selectedID = selectedID }
    func loadGroups() async throws -> [PlayGroup] { groups }
    func selectedGroupID() async -> String? { selectedID }
    func selectGroup(_ id: String?) async { selectedID = id }
    func createGroup(name: String) async throws -> PlayGroup {
        let group = PlayGroup(id: "created", name: name, avatarColor: "#9ADE28", sportCode: "badminton_doubles", memberCount: 1, matchCount: 0, myRole: .owner, sessionStart: nil, sessionEnd: nil)
        groups.append(group); return group
    }
    func joinGroup(code: String) async throws -> PlayGroup { try await createGroup(name: "Joined") }
    func renameGroup(id: String, name: String) async throws -> PlayGroup { try await createGroup(name: name) }
    func createInvite(groupID: String) async throws -> GroupInvite { GroupInvite(code: "CODE", expiresAt: nil) }
    func loadRoster(groupID: String) async throws -> GroupRoster { GroupRoster(members: [], guests: []) }
    func addMember(groupID: String, email: String, displayName: String) async throws -> GroupMember { throw GroupRepositoryError.invalidResponse }
    func removeMember(groupID: String, userID: String) async throws {}
    func changeRole(groupID: String, userID: String, role: GroupRole) async throws -> GroupMember { throw GroupRepositoryError.invalidResponse }
    func updateSession(groupID: String, start: String?, end: String?) async throws -> PlayGroup { throw GroupRepositoryError.invalidResponse }
}
