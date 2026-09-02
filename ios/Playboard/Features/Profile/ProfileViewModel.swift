import Combine
import Foundation

/// Immutable state for the Profile tab.
struct ProfileUiState: Equatable, Sendable {
    var groupID: String?
    var stats: PlayerStats?
    var partners: [PartnerStats] = []
    var isLoading = false
    var isLoadingPartners = false
    var partnersExpanded = false
    var errorMessage: String?
    var partnersError: String?
    var name = ""
    var isSavingName = false
    var saveMessage: String?
}

/// Loads group-scoped profile data and keeps partner loading independent.
@MainActor
final class ProfileViewModel: ObservableObject {
    @Published var state: ProfileUiState
    private let repository: any ProfileRepository
    private let userID: String
    private var task: Task<Void, Never>?

    init(repository: any ProfileRepository, user: AuthenticatedUser) {
        self.repository = repository; userID = user.id
        state = ProfileUiState(name: user.displayName)
    }

    func select(group: PlayGroup?) { guard state.groupID != group?.id else { return }; task?.cancel(); state.groupID = group?.id; state.stats = nil; state.partners = []; state.partnersExpanded = false; guard let group else { state.isLoading = false; return }; task = Task { await load(groupID: group.id) } }
    func retry() { guard let groupID = state.groupID else { return }; task = Task { await load(groupID: groupID) } }
    func togglePartners() { state.partnersExpanded.toggle(); if state.partnersExpanded, state.partners.isEmpty { loadPartners() } }

    func saveName() { let value = state.name.trimmingCharacters(in: .whitespacesAndNewlines); guard value.count >= 2, let groupID = state.groupID else { state.saveMessage = "Enter at least 2 characters."; return }; state.name = value; state.isSavingName = true; state.saveMessage = nil; Task { do { _ = try await repository.updateDisplayName(value); state.isSavingName = false; state.saveMessage = "Name updated."; await load(groupID: groupID) } catch { state.isSavingName = false; state.saveMessage = "Could not update your name." } } }

    private func load(groupID: String) async { state.isLoading = true; state.errorMessage = nil; do { let stats = try await repository.stats(groupID: groupID, userID: userID); guard !Task.isCancelled else { return }; state.stats = stats; state.name = stats.displayName; state.isLoading = false } catch { state.isLoading = false; state.errorMessage = "Profile statistics could not be loaded." } }
    private func loadPartners() { guard let groupID = state.groupID else { return }; state.isLoadingPartners = true; state.partnersError = nil; Task { do { state.partners = try await repository.partners(groupID: groupID, userID: userID); state.isLoadingPartners = false } catch { state.isLoadingPartners = false; state.partnersError = "Partners could not be loaded." } } }
}
