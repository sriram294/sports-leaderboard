import Combine
import Foundation

/// Owns range state, deterministic month boundaries, cancellation, and stale-data behavior.
@MainActor
final class BoardViewModel: ObservableObject {
    @Published private(set) var state = BoardUiState()

    private let repository: any LeaderboardRepository
    private let clock: any PlayboardClock
    private let calendar: any LeaderboardCalendaring
    private var loadTask: Task<Void, Never>?

    init(
        repository: any LeaderboardRepository,
        clock: any PlayboardClock = SystemPlayboardClock(),
        calendar: any LeaderboardCalendaring = SystemLeaderboardCalendar()
    ) {
        self.repository = repository
        self.clock = clock
        self.calendar = calendar
        state.monthName = calendar.monthName(containing: clock.now)
    }

    func select(group: PlayGroup?) {
        if state.groupID == group?.id {
            state.groupMemberCount = group?.memberCount ?? 0
            return
        }
        loadTask?.cancel()
        state.groupID = group?.id
        state.groupMemberCount = group?.memberCount ?? 0
        state.metric = .rating
        state.selectedSnapshotMonth = nil
        state.availableSnapshotMonths = []
        state.rankings = []
        state.errorMessage = nil
        guard group != nil else { state.isLoading = false; return }
        startLoad(showLoading: true)
    }

    func setSnapshotMonth(_ month: String?) {
        guard month != state.selectedSnapshotMonth else { return }
        state.selectedSnapshotMonth = month
        startLoad(showLoading: true)
    }

    func cycleMetric() { state.metric = state.metric.next }

    func retry() { startLoad(showLoading: true) }

    func refresh() async {
        loadTask?.cancel()
        state.isRefreshing = true
        await load(showLoading: false)
        state.isRefreshing = false
    }

    private func startLoad(showLoading: Bool) {
        loadTask?.cancel()
        loadTask = Task { [weak self] in await self?.load(showLoading: showLoading) }
    }

    private func load(showLoading: Bool) async {
        guard let groupID = state.groupID else { return }
        if showLoading { state.isLoading = true }
        state.errorMessage = nil
        state.staleMessage = nil
        state.monthName = calendar.monthName(containing: clock.now)
        do {
            let months = try await repository.archivedMonths(groupID: groupID)
            try Task.checkCancellation()
            guard state.groupID == groupID else { return }
            state.availableSnapshotMonths = months
            if let selected = state.selectedSnapshotMonth, !months.contains(selected) {
                state.selectedSnapshotMonth = nil
            }
            let leaderboard: Leaderboard
            if let month = state.selectedSnapshotMonth {
                leaderboard = try await repository.archivedLeaderboard(groupID: groupID, month: month)
            } else {
                leaderboard = try await repository.leaderboard(
                    groupID: groupID, window: calendar.monthInterval(containing: clock.now))
            }
            try Task.checkCancellation()
            guard state.groupID == groupID else { return }
            state.rankings = leaderboard.rankings
            state.minGamesToRank = max(1, leaderboard.minGamesToRank)
            state.isLoading = false
        } catch is CancellationError {
            return
        } catch {
            state.isLoading = false
            let message = (error as? GroupRepositoryError)?.message ?? GroupRepositoryError.invalidResponse.message
            if state.rankings.isEmpty { state.errorMessage = message }
            else { state.staleMessage = "Showing saved rankings. \(message)" }
        }
    }
}
