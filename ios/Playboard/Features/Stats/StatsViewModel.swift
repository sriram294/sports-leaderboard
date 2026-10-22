import Combine
import Foundation

/// Loads group insights for the active group and the selected time range.
@MainActor
final class StatsViewModel: ObservableObject {
    @Published private(set) var state = StatsUiState()

    private let leaderboardRepository: any LeaderboardRepository
    private let matchRepository: any MatchRepository
    private let profileRepository: any ProfileRepository
    private let currentUserID: String
    private let clock: any PlayboardClock
    private let calendar: any LeaderboardCalendaring
    private var loadTask: Task<Void, Never>?
    private var partnersTask: Task<Void, Never>?

    init(
        leaderboardRepository: any LeaderboardRepository,
        matchRepository: any MatchRepository,
        profileRepository: any ProfileRepository,
        currentUserID: String,
        clock: any PlayboardClock = SystemPlayboardClock(),
        calendar: any LeaderboardCalendaring = SystemLeaderboardCalendar()
    ) {
        self.leaderboardRepository = leaderboardRepository
        self.matchRepository = matchRepository
        self.profileRepository = profileRepository
        self.currentUserID = currentUserID
        self.clock = clock
        self.calendar = calendar
    }

    func select(group: PlayGroup?) {
        state.groupMatchCount = group?.matchCount ?? 0
        guard state.groupID != group?.id else { return }
        loadTask?.cancel()
        partnersTask?.cancel()
        state = StatsUiState(groupID: group?.id, groupMatchCount: group?.matchCount ?? 0, range: state.range)
        guard group != nil else { return }
        startLoad(showLoading: true)
    }

    func setRange(_ range: StatsTimeRange) {
        guard range != state.range else { return }
        state.range = range
        let reopenPartners = state.partnersExpanded
        startLoad(showLoading: true, reopenPartners: reopenPartners)
    }

    func retry() { startLoad(showLoading: true) }

    func refresh() async {
        loadTask?.cancel()
        state.isRefreshing = true
        await load(showLoading: false)
        state.isRefreshing = false
        if state.partnersExpanded { await fetchPartners() }
    }

    /// Expands the partners card, defaulting the picker to the signed-in user, or collapses it.
    func togglePartners() {
        if state.partnersExpanded {
            partnersTask?.cancel()
            state.partnersExpanded = false
            return
        }
        state.partnersExpanded = true
        if state.selectedPlayer == nil {
            state.selectedPlayerID = state.players.first { $0.userID == currentUserID }?.userID ?? state.players.first?.userID
        }
        fetchPartnersInBackground()
    }

    func selectPlayer(_ userID: String) {
        guard userID != state.selectedPlayerID else { return }
        state.selectedPlayerID = userID
        state.partners = []
        fetchPartnersInBackground()
    }

    private var window: DateInterval? {
        state.range == .month ? calendar.monthInterval(containing: clock.now) : nil
    }

    private func startLoad(showLoading: Bool, reopenPartners: Bool = false) {
        loadTask?.cancel()
        partnersTask?.cancel()
        loadTask = Task { [weak self] in
            guard let self else { return }
            await load(showLoading: showLoading)
            if reopenPartners, state.selectedPlayer != nil {
                state.partnersExpanded = true
                await fetchPartners()
            }
        }
    }

    private func load(showLoading: Bool) async {
        guard let groupID = state.groupID else { return }
        let window = window
        if showLoading {
            state.isLoading = true
            state.partnersExpanded = false
            state.partners = []
            state.partnersError = nil
        }
        state.errorMessage = nil
        do {
            async let leaderboard = leaderboardRepository.leaderboard(groupID: groupID, window: window)
            async let page = matchRepository.matches(groupID: groupID, cursor: nil, mine: false)
            let rankings = try await leaderboard.rankings
            let matches = try await page.matches
            // Monthly winners are secondary: the dashboard still renders without them.
            let winners = (try? await profileRepository.trophies(groupID: groupID, limit: groupTrophyLimit)) ?? []
            try Task.checkCancellation()
            guard state.groupID == groupID else { return }
            let visible = window.map { window in matches.filter { window.start <= $0.playedAt && $0.playedAt < window.end } } ?? matches
            let matchCount = window == nil ? state.groupMatchCount : rankings.reduce(0) { $0 + $1.gamesPlayed } / 4
            state.records = StatsRecords(rankings: rankings, matchCount: matchCount)
            state.biggestWin = BiggestWin(matches: visible)
            state.hasMatches = !rankings.isEmpty || !visible.isEmpty
            state.monthlyWinners = winners
            state.players = rankings
            if let selected = state.selectedPlayerID, !rankings.contains(where: { $0.userID == selected }) {
                state.selectedPlayerID = nil
            }
            state.isLoading = false
        } catch is CancellationError {
            return
        } catch {
            guard state.groupID == groupID else { return }
            state.isLoading = false
            if state.records == nil {
                state.errorMessage = (error as? GroupRepositoryError)?.message
                    ?? (error as? MatchRepositoryError)?.message
                    ?? GroupRepositoryError.invalidResponse.message
            }
        }
    }

    private func fetchPartnersInBackground() {
        partnersTask?.cancel()
        partnersTask = Task { [weak self] in await self?.fetchPartners() }
    }

    private func fetchPartners() async {
        guard let groupID = state.groupID, let userID = state.selectedPlayerID else { return }
        state.isLoadingPartners = true
        state.partnersError = nil
        do {
            let partners = try await profileRepository.partners(groupID: groupID, userID: userID, window: window)
            try Task.checkCancellation()
            guard state.selectedPlayerID == userID, state.groupID == groupID else { return }
            state.partners = partners
            state.isLoadingPartners = false
        } catch is CancellationError {
            return
        } catch {
            guard state.selectedPlayerID == userID else { return }
            state.isLoadingPartners = false
            state.partnersError = "Partners could not be loaded."
        }
    }
}
