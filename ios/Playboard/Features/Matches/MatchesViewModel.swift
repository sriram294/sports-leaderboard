import Combine
import Foundation

/// Owns group-scoped pagination, filtering, refresh, and one expanded match at a time.
@MainActor
final class MatchesViewModel: ObservableObject {
    @Published private(set) var state = MatchesUiState()
    private let repository: any MatchRepository
    private var loadTask: Task<Void, Never>?
    private var detailTask: Task<Void, Never>?

    init(repository: any MatchRepository, currentUserID: String = "") {
        self.repository = repository
        state.currentUserID = currentUserID
    }

    func select(group: PlayGroup?) {
        state.canModerate = group?.canManage ?? false
        if state.groupID == group?.id {
            state.groupMatchCount = group?.matchCount ?? 0
            return
        }
        loadTask?.cancel()
        detailTask?.cancel()
        state = MatchesUiState(
            groupID: group?.id,
            groupMatchCount: group?.matchCount ?? 0,
            currentUserID: state.currentUserID,
            canModerate: group?.canManage ?? false
        )
        guard group != nil else { return }
        startLoad(showLoading: true)
    }

    /// Whether a day's section is expanded: a user override, else only the newest day.
    func isDayExpanded(_ day: Date, calendar: Calendar = .current) -> Bool {
        state.expandedDays[day] ?? (day == sections(calendar: calendar).first?.day)
    }

    func toggleDay(_ day: Date, calendar: Calendar = .current) {
        state.expandedDays[day] = !isDayExpanded(day, calendar: calendar)
    }

    func requestDelete(matchID: String) {
        state.deleteErrorMessage = nil
        state.pendingDeletionID = matchID
    }

    func cancelDelete() {
        guard !state.isDeleting else { return }
        state.pendingDeletionID = nil
    }

    func confirmDelete() async {
        guard let groupID = state.groupID, let matchID = state.pendingDeletionID, !state.isDeleting else { return }
        state.isDeleting = true
        do {
            try await repository.delete(groupID: groupID, matchID: matchID)
            state.matches.removeAll { $0.id == matchID }
            if state.expandedMatchID == matchID {
                state.expandedMatchID = nil
                state.detail = nil
            }
            state.groupMatchCount = max(0, state.groupMatchCount - 1)
            state.lastDeletedMatchID = matchID
        } catch {
            state.deleteErrorMessage = message(for: error)
        }
        state.isDeleting = false
        state.pendingDeletionID = nil
    }

    func retry() { startLoad(showLoading: true) }

    func refresh() async {
        loadTask?.cancel()
        state.isRefreshing = true
        await load(showLoading: false)
        state.isRefreshing = false
        // An edit changes the open card's breakdown, so re-fetch it too.
        if let groupID = state.groupID, let matchID = state.expandedMatchID,
           let detail = try? await repository.detail(groupID: groupID, matchID: matchID),
           state.expandedMatchID == matchID {
            state.detail = detail
        }
    }

    func toggleMineOnly() {
        state.mineOnly.toggle()
        state.matches = []
        state.expandedMatchID = nil
        state.detail = nil
        startLoad(showLoading: true)
    }

    func loadMore() {
        guard let groupID = state.groupID, let cursor = state.nextCursor, !state.isLoadingMore else { return }
        let mineOnly = state.mineOnly
        state.isLoadingMore = true
        Task { [weak self] in
            guard let self else { return }
            do {
                let page = try await repository.matches(groupID: groupID, cursor: cursor, mine: mineOnly)
                try Task.checkCancellation()
                guard state.groupID == groupID, state.mineOnly == mineOnly else { return }
                let known = Set(state.matches.map(\.id))
                state.matches.append(contentsOf: page.matches.filter { !known.contains($0.id) })
                state.nextCursor = page.nextCursor
                state.isLoadingMore = false
            } catch is CancellationError {
                return
            } catch {
                state.isLoadingMore = false
                state.staleMessage = message(for: error)
            }
        }
    }

    func toggleDetail(matchID: String) {
        detailTask?.cancel()
        if state.expandedMatchID == matchID {
            state.expandedMatchID = nil
            state.detail = nil
            state.detailErrorMessage = nil
            return
        }
        guard let groupID = state.groupID else { return }
        state.expandedMatchID = matchID
        state.detail = nil
        state.isDetailLoading = true
        state.detailErrorMessage = nil
        detailTask = Task { [weak self] in
            guard let self else { return }
            do {
                let detail = try await repository.detail(groupID: groupID, matchID: matchID)
                try Task.checkCancellation()
                guard state.groupID == groupID, state.expandedMatchID == matchID else { return }
                state.detail = detail
                state.isDetailLoading = false
            } catch is CancellationError {
                return
            } catch {
                guard state.expandedMatchID == matchID else { return }
                state.isDetailLoading = false
                state.detailErrorMessage = message(for: error)
            }
        }
    }

    func sections(calendar: Calendar = .current) -> [MatchDaySection] {
        Dictionary(grouping: state.matches) { calendar.startOfDay(for: $0.playedAt) }
            .map { MatchDaySection(day: $0.key, matches: $0.value.sorted { $0.playedAt > $1.playedAt }) }
            .sorted { $0.day > $1.day }
    }

    private func startLoad(showLoading: Bool) {
        loadTask?.cancel()
        loadTask = Task { [weak self] in await self?.load(showLoading: showLoading) }
    }

    private func load(showLoading: Bool) async {
        guard let groupID = state.groupID else { return }
        let mineOnly = state.mineOnly
        if showLoading { state.isLoading = true }
        state.errorMessage = nil
        state.staleMessage = nil
        do {
            let page = try await repository.matches(groupID: groupID, cursor: nil, mine: mineOnly)
            try Task.checkCancellation()
            guard state.groupID == groupID, state.mineOnly == mineOnly else { return }
            state.matches = page.matches
            state.nextCursor = page.nextCursor
            state.isLoading = false
        } catch is CancellationError {
            return
        } catch {
            state.isLoading = false
            if state.matches.isEmpty { state.errorMessage = message(for: error) }
            else { state.staleMessage = "Showing saved matches. \(message(for: error))" }
        }
    }

    private func message(for error: Error) -> String {
        (error as? MatchRepositoryError)?.message ?? MatchRepositoryError.invalidResponse.message
    }
}
