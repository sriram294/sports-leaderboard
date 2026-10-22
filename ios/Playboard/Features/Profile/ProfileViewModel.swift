import Combine
import Foundation

/// The rename sheet; nil on ``ProfileUiState/renameSheet`` means it is closed.
struct EditNameState: Equatable, Sendable {
    var input: String
    var isSubmitting = false
    var hasFailed = false

    var canSubmit: Bool { !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSubmitting }
}

/// Immutable state for the own-profile tab and a leaderboard player drill-down.
struct ProfileUiState: Equatable, Sendable {
    var groupID: String?
    var groupName: String?
    /// The signed-in user, kept live so a rename or avatar change shows immediately.
    var ownUser: AuthenticatedUser
    /// Whose profile is shown; equal to `ownUser.id` for the Profile tab.
    var viewedUserID: String
    var stats: PlayerStats?
    var isLoading = false
    var errorMessage: String?
    /// Heatmap months (oldest first) and the local days the player was in a match.
    var attendanceMonths: [Date] = []
    var attendanceDays: Set<Date> = []
    var partnersExpanded = false
    var partners: [PartnerStats] = []
    var isLoadingPartners = false
    var partnersError: String?
    var renameSheet: EditNameState?
    var isUpdatingAvatar = false
    var updateError: String?

    var isOwnProfile: Bool { viewedUserID == ownUser.id }

    /// Identity shown on the hero card; the live session wins for the own profile.
    var displayName: String { isOwnProfile ? ownUser.displayName : stats?.displayName ?? "" }
    var photoURL: String? { isOwnProfile ? ownUser.photoURL : stats?.photoURL }
    var avatarID: String? { isOwnProfile ? ownUser.avatarID : stats?.avatarID }
    var avatarColor: String { isOwnProfile ? ownUser.avatarColor : stats?.avatarColor ?? "#9ADE28" }

    /// Recent matches framed from the viewed player's perspective.
    var recentMatches: [RecentMatchRow] {
        guard let stats else { return [] }
        return stats.recentMatches.compactMap { RecentMatchRow(match: $0, userID: stats.userID) }
    }
}

/// Bundled default avatars, in display order.
let defaultAvatarIDs: [String] = (0..<16).map { "avatar\($0)" }

/// Loads a player's group-scoped profile and owns the own-profile editing flows.
@MainActor
final class ProfileViewModel: ObservableObject {
    @Published private(set) var state: ProfileUiState

    private let repository: any ProfileRepository
    private let clock: any PlayboardClock
    private let grid: AttendanceGrid
    private let onUserUpdated: @MainActor (AuthenticatedUser) -> Void
    private var loadTask: Task<Void, Never>?
    private var partnersTask: Task<Void, Never>?

    /// - Parameter viewedUserID: another player's id for a leaderboard drill-down; nil for the own profile.
    init(
        repository: any ProfileRepository,
        user: AuthenticatedUser,
        viewedUserID: String? = nil,
        clock: any PlayboardClock = SystemPlayboardClock(),
        grid: AttendanceGrid = AttendanceGrid(),
        onUserUpdated: @escaping @MainActor (AuthenticatedUser) -> Void = { _ in }
    ) {
        self.repository = repository
        self.clock = clock
        self.grid = grid
        self.onUserUpdated = onUserUpdated
        state = ProfileUiState(ownUser: user, viewedUserID: viewedUserID ?? user.id)
    }

    /// Scopes the profile to the active group, reloading only when it actually changes.
    func select(group: PlayGroup?) {
        state.groupName = group?.name
        guard state.groupID != group?.id else { return }
        state.groupID = group?.id
        startLoad(showLoading: true)
    }

    func retry() { startLoad(showLoading: true) }

    /// Silent reload after a match is recorded, edited, or deleted elsewhere.
    func refresh() async {
        loadTask?.cancel()
        await load(showLoading: false)
        if state.partnersExpanded { await fetchPartners() }
    }

    func togglePartners() {
        if state.partnersExpanded {
            partnersTask?.cancel()
            state.partnersExpanded = false
            return
        }
        state.partnersExpanded = true
        partnersTask?.cancel()
        partnersTask = Task { [weak self] in await self?.fetchPartners() }
    }

    // MARK: Own-profile editing

    func beginRename() {
        guard state.isOwnProfile else { return }
        state.renameSheet = EditNameState(input: state.displayName)
        state.updateError = nil
    }

    func setRenameInput(_ value: String) {
        state.renameSheet?.input = value
        state.renameSheet?.hasFailed = false
    }

    func dismissRename() { state.renameSheet = nil }

    func submitRename() async {
        guard let sheet = state.renameSheet, sheet.canSubmit else { return }
        state.renameSheet?.isSubmitting = true
        state.renameSheet?.hasFailed = false
        do {
            let user = try await repository.updateDisplayName(sheet.input.trimmingCharacters(in: .whitespacesAndNewlines))
            applyUpdatedUser(user)
            state.renameSheet = nil
        } catch {
            state.renameSheet?.isSubmitting = false
            state.renameSheet?.hasFailed = true
        }
    }

    func selectAvatar(_ avatarID: String) async {
        guard state.isOwnProfile else { return }
        state.isUpdatingAvatar = true
        state.updateError = nil
        do { applyUpdatedUser(try await repository.updateAvatar(avatarID)) }
        catch { state.updateError = "Couldn't update your avatar. Please try again." }
        state.isUpdatingAvatar = false
    }

    func uploadPhoto(_ data: Data, mimeType: String) async {
        guard state.isOwnProfile else { return }
        state.isUpdatingAvatar = true
        state.updateError = nil
        do { applyUpdatedUser(try await repository.uploadPhoto(data, mimeType: mimeType)) }
        catch { state.updateError = "Couldn't upload photo. Please try again." }
        state.isUpdatingAvatar = false
    }

    /// Mirrors an identity change made outside this view model (for example another profile instance).
    func updateOwnUser(_ user: AuthenticatedUser) {
        guard user.id == state.ownUser.id else { return }
        state.ownUser = user
    }

    // MARK: Loading

    private func applyUpdatedUser(_ user: AuthenticatedUser) {
        state.ownUser = user
        onUserUpdated(user)
    }

    private func startLoad(showLoading: Bool) {
        loadTask?.cancel()
        partnersTask?.cancel()
        loadTask = Task { [weak self] in await self?.load(showLoading: showLoading) }
    }

    private func load(showLoading: Bool) async {
        guard let groupID = state.groupID else {
            state.stats = nil
            state.isLoading = false
            state.errorMessage = nil
            return
        }
        let userID = state.viewedUserID
        let months = grid.months(endingAt: clock.now)
        state.attendanceMonths = months
        state.errorMessage = nil
        if showLoading {
            // Drop the previous group's data so a failure can never show it under the new group.
            state.isLoading = true
            state.stats = nil
            state.attendanceDays = []
            state.partnersExpanded = false
            state.partners = []
            state.partnersError = nil
        }
        do {
            let stats = try await repository.stats(groupID: groupID, userID: userID)
            try Task.checkCancellation()
            guard state.groupID == groupID else { return }
            state.stats = stats
            state.isLoading = false
        } catch is CancellationError {
            return
        } catch {
            guard state.groupID == groupID else { return }
            state.isLoading = false
            if state.stats == nil { state.errorMessage = "Couldn't load \(state.isOwnProfile ? "your" : "this player's") stats." }
            return
        }
        // Attendance is secondary: a failure leaves the heatmap empty rather than failing the page.
        guard let window = grid.window(for: months),
              let instants = try? await repository.attendance(groupID: groupID, userID: userID, window: window),
              !Task.isCancelled, state.groupID == groupID else { return }
        state.attendanceDays = grid.days(from: instants)
    }

    private func fetchPartners() async {
        guard let groupID = state.groupID else { return }
        state.isLoadingPartners = true
        state.partnersError = nil
        do {
            let partners = try await repository.partners(groupID: groupID, userID: state.viewedUserID, window: nil)
            try Task.checkCancellation()
            guard state.partnersExpanded, state.groupID == groupID else { return }
            state.partners = partners
            state.isLoadingPartners = false
        } catch is CancellationError {
            return
        } catch {
            state.isLoadingPartners = false
            state.partnersError = "Partners could not be loaded."
        }
    }
}
