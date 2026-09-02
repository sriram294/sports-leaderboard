import Foundation

/// Editable raw score values so partially typed input remains representable.
struct MatchSetDraft: Equatable, Identifiable, Sendable {
    let id: UUID
    var team1: String
    var team2: String

    init(id: UUID = UUID(), team1: String = "", team2: String = "") {
        self.id = id
        self.team1 = team1
        self.team2 = team2
    }

    var score: (Int, Int)? {
        guard let first = Int(team1), let second = Int(team2), first >= 0, second >= 0 else { return nil }
        return (first, second)
    }
}

/// Immutable presentation state for recording one doubles match.
struct AddMatchUiState: Equatable, Sendable {
    var groupID: String?
    var isLoading = false
    var roster: [GroupMember] = []
    var team1: [String] = []
    var team2: [String] = []
    var sets: [MatchSetDraft] = [MatchSetDraft()]
    var selectedWinner: Int?
    var isSubmitting = false
    var errorMessage: String?
    var successMessage: String?
    var successfulMatchID: String?

    var assignedPlayerIDs: Set<String> { Set(team1 + team2) }
    var isDirty: Bool {
        !team1.isEmpty || !team2.isEmpty || sets.count != 1 || sets.first?.team1.isEmpty == false ||
            sets.first?.team2.isEmpty == false || selectedWinner != nil
    }

    var parsedSets: [(Int, Int)]? {
        let values = sets.compactMap(\.score)
        guard !sets.isEmpty, values.count == sets.count, values.allSatisfy({ $0.0 != $0.1 }) else { return nil }
        return values
    }

    var derivedWinner: Int? {
        guard let parsedSets else { return nil }
        let team1Wins = parsedSets.count { $0.0 > $0.1 }
        let team2Wins = parsedSets.count { $0.1 > $0.0 }
        guard team1Wins != team2Wins else { return nil }
        return team1Wins > team2Wins ? 1 : 2
    }

    var validationMessage: String? {
        guard team1.count == 2, team2.count == 2, assignedPlayerIDs.count == 4 else {
            return "Choose four different active players."
        }
        guard parsedSets != nil, let derivedWinner else {
            return "Enter complete, non-tied set scores that determine a winner."
        }
        guard selectedWinner == derivedWinner else {
            return "Confirm the team that won the most sets."
        }
        return nil
    }

    var canSubmit: Bool { !isSubmitting && validationMessage == nil }

    func member(_ id: String) -> GroupMember? { roster.first { $0.userID == id } }
    func players(for team: Int) -> [GroupMember] { (team == 1 ? team1 : team2).compactMap(member) }
}
