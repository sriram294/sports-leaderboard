import Combine
import Foundation

/// Owns deterministic team/score validation and duplicate-safe match submission.
@MainActor
final class AddMatchViewModel: ObservableObject {
    @Published private(set) var state = AddMatchUiState()

    private let matchRepository: any MatchRepository
    private let groupRepository: any GroupRepository
    private let clock: any PlayboardClock
    private let requestIDs: any MatchRequestIDGenerating
    private var loadTask: Task<Void, Never>?
    private var pendingRequestID: String?
    private var pendingRequest: RecordMatchRequest?

    init(
        matchRepository: any MatchRepository,
        groupRepository: any GroupRepository,
        clock: any PlayboardClock = SystemPlayboardClock(),
        requestIDs: any MatchRequestIDGenerating = UUIDMatchRequestIDGenerator()
    ) {
        self.matchRepository = matchRepository
        self.groupRepository = groupRepository
        self.clock = clock
        self.requestIDs = requestIDs
    }

    func select(group: PlayGroup?) {
        guard state.groupID != group?.id else { return }
        loadTask?.cancel()
        pendingRequestID = nil
        pendingRequest = nil
        state = AddMatchUiState(groupID: group?.id, isLoading: group != nil)
        guard let group else { return }
        loadTask = Task { [weak self] in await self?.loadRoster(groupID: group.id) }
    }

    /// Enters edit mode, pre-filling the form from `matchID`'s current detail.
    func startEditing(matchID: String) {
        guard let groupID = state.groupID, state.editingMatchID != matchID else { return }
        loadTask?.cancel()
        pendingRequestID = nil
        pendingRequest = nil
        state = AddMatchUiState(groupID: groupID, isLoading: true, roster: state.roster, editingMatchID: matchID)
        loadTask = Task { [weak self] in await self?.loadForEditing(groupID: groupID, matchID: matchID) }
    }

    /// Leaves edit mode without saving and returns to a blank create form.
    func cancelEditing() {
        guard state.isEditing else { return }
        loadTask?.cancel()
        discardChanges()
    }

    func retryRoster() {
        guard let groupID = state.groupID else { return }
        loadTask?.cancel()
        loadTask = Task { [weak self] in await self?.loadRoster(groupID: groupID) }
    }

    func addPlayer(_ userID: String, to team: Int) {
        guard (team == 1 || team == 2), state.roster.contains(where: { $0.userID == userID }),
              !state.assignedPlayerIDs.contains(userID), (team == 1 ? state.team1.count : state.team2.count) < 2 else { return }
        if team == 1 { state.team1.append(userID) } else { state.team2.append(userID) }
        didMutateForm()
    }

    func removePlayer(_ userID: String) {
        state.team1.removeAll { $0 == userID }
        state.team2.removeAll { $0 == userID }
        didMutateForm()
    }

    func setScore(id: UUID, team: Int, value: String) {
        guard let index = state.sets.firstIndex(where: { $0.id == id }), team == 1 || team == 2 else { return }
        let digits = String(value.filter(\.isNumber).prefix(2))
        if team == 1 { state.sets[index].team1 = digits } else { state.sets[index].team2 = digits }
        state.selectedWinner = state.derivedWinner
        didMutateForm(preserveWinner: true)
    }

    func addSet() {
        state.sets.append(MatchSetDraft())
        didMutateForm()
    }

    func removeSet(id: UUID) {
        guard state.sets.count > 1 else { return }
        state.sets.removeAll { $0.id == id }
        state.selectedWinner = state.derivedWinner
        didMutateForm(preserveWinner: true)
    }

    func selectWinner(_ team: Int) {
        guard team == 1 || team == 2 else { return }
        state.selectedWinner = team
        didMutateForm(preserveWinner: true)
    }

    func submit() async {
        guard !state.isSubmitting, state.canSubmit, let groupID = state.groupID,
              let scores = state.parsedSets, let winner = state.selectedWinner else { return }
        if let matchID = state.editingMatchID {
            await submitEdit(groupID: groupID, matchID: matchID, scores: scores, winner: winner)
            return
        }
        let requestID = pendingRequestID ?? requestIDs.next()
        pendingRequestID = requestID
        let request = pendingRequest ?? RecordMatchRequest(
            playedAt: clock.now,
            teams: [.init(teamNo: 1, playerIds: state.team1), .init(teamNo: 2, playerIds: state.team2)],
            sets: scores.enumerated().map {
                .init(setNo: $0.offset + 1, team1Score: $0.element.0, team2Score: $0.element.1)
            },
            winningTeamNo: winner
        )
        pendingRequest = request
        state.isSubmitting = true
        state.errorMessage = nil
        do {
            let match = try await matchRepository.record(groupID: groupID, request: request, requestID: requestID)
            state.isSubmitting = false
            state.successfulMatchID = match.id
            state.lastSuccessWasEdit = false
            state.successMessage = "Match recorded."
            pendingRequestID = nil
            pendingRequest = nil
        } catch is CancellationError {
            state.isSubmitting = false
        } catch {
            state.isSubmitting = false
            state.errorMessage = (error as? MatchRepositoryError)?.message ?? MatchRepositoryError.invalidResponse.message
        }
    }

    func resetAfterSuccess() {
        guard state.successfulMatchID != nil else { return }
        discardChanges()
    }

    func discardChanges() {
        pendingRequestID = nil
        pendingRequest = nil
        state = AddMatchUiState(groupID: state.groupID, roster: state.roster)
    }

    private func submitEdit(groupID: String, matchID: String, scores: [(Int, Int)], winner: Int) async {
        // An edit must round-trip the original played time; without it the form never finished loading.
        guard let playedAt = state.editingPlayedAt else { return }
        let request = RecordMatchRequest(
            playedAt: playedAt,
            teams: [.init(teamNo: 1, playerIds: state.team1), .init(teamNo: 2, playerIds: state.team2)],
            sets: scores.enumerated().map { .init(setNo: $0.offset + 1, team1Score: $0.element.0, team2Score: $0.element.1) },
            winningTeamNo: winner
        )
        state.isSubmitting = true
        state.errorMessage = nil
        do {
            let match = try await matchRepository.edit(groupID: groupID, matchID: matchID, request: request)
            state.isSubmitting = false
            state.successfulMatchID = match.id
            state.lastSuccessWasEdit = true
            state.successMessage = "Match updated."
        } catch is CancellationError {
            state.isSubmitting = false
        } catch {
            state.isSubmitting = false
            state.errorMessage = (error as? MatchRepositoryError)?.message ?? MatchRepositoryError.invalidResponse.message
        }
    }

    private func loadForEditing(groupID: String, matchID: String) async {
        do {
            async let rosterResult = groupRepository.loadRoster(groupID: groupID)
            async let detailResult = matchRepository.detail(groupID: groupID, matchID: matchID)
            let roster = try await rosterResult
            let detail = try await detailResult
            try Task.checkCancellation()
            guard state.groupID == groupID, state.editingMatchID == matchID else { return }
            var members = roster.members + roster.guests
            // A player who has since left the group still appears in the match being edited.
            for player in detail.teams.flatMap(\.players) where !members.contains(where: { $0.userID == player.userID }) {
                members.append(GroupMember(userID: player.userID, displayName: player.displayName, photoURL: player.photoURL,
                                           avatarID: player.avatarID, avatarColor: player.avatarColor, role: .member))
            }
            state.roster = members
            state.team1 = detail.team(1)?.players.map(\.userID) ?? []
            state.team2 = detail.team(2)?.players.map(\.userID) ?? []
            state.sets = detail.sets.sorted { $0.setNo < $1.setNo }.map { MatchSetDraft(team1: String($0.team1Score), team2: String($0.team2Score)) }
            if state.sets.isEmpty { state.sets = [MatchSetDraft()] }
            state.selectedWinner = detail.winningTeam?.teamNo
            state.editingPlayedAt = detail.playedAt
            state.isLoading = false
        } catch is CancellationError {
            return
        } catch {
            guard state.editingMatchID == matchID else { return }
            state.isLoading = false
            state.errorMessage = (error as? MatchRepositoryError)?.message
                ?? (error as? GroupRepositoryError)?.message
                ?? MatchRepositoryError.invalidResponse.message
        }
    }

    private func loadRoster(groupID: String) async {
        state.isLoading = true
        state.errorMessage = nil
        do {
            let roster = try await groupRepository.loadRoster(groupID: groupID)
            try Task.checkCancellation()
            guard state.groupID == groupID else { return }
            state.roster = roster.members + roster.guests
            state.isLoading = false
        } catch is CancellationError {
            return
        } catch {
            state.isLoading = false
            state.errorMessage = (error as? GroupRepositoryError)?.message ?? GroupRepositoryError.invalidResponse.message
        }
    }

    private func didMutateForm(preserveWinner: Bool = false) {
        pendingRequestID = nil
        pendingRequest = nil
        state.errorMessage = nil
        state.successMessage = nil
        state.successfulMatchID = nil
        if !preserveWinner { state.selectedWinner = state.derivedWinner }
    }
}
