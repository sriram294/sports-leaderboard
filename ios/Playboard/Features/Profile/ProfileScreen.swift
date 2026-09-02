import SwiftUI

/// Profile and personal group statistics screen.
struct ProfileScreen: View {
    @ObservedObject var viewModel: ProfileViewModel
    let email: String
    let signOut: () -> Void
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        ScrollView { content.padding(PlayboardSpacing.extraLarge) }
            .refreshable { viewModel.retry() }
            .accessibilityIdentifier("profile-screen")
    }

    @ViewBuilder private var content: some View {
        if viewModel.state.isLoading { PlayboardLoadingView(message: "Loading your profile…") }
        else if let error = viewModel.state.errorMessage { PlayboardErrorView(message: error) { viewModel.retry() } }
        else if let stats = viewModel.state.stats {
                VStack(alignment: .leading, spacing: PlayboardSpacing.large) {
                identity(stats); nameEditor; statGrid(stats); finishes(stats); partners; account
            }
        } else { PlayboardEmptyView(title: "Choose a group", message: "Select a group to see your profile statistics.") }
    }

    private func identity(_ stats: PlayerStats) -> some View { PlayboardCard { HStack(spacing: PlayboardSpacing.medium) { PlayerAvatar(displayName: stats.displayName, avatarID: nil, photoURL: stats.photoURL, color: Color(playboardHex: stats.avatarColor) ?? palette.brand, size: 64); VStack(alignment: .leading) { Text(stats.displayName).font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary); Text("\(stats.matchesPlayed) matches · \(stats.winRate, format: .percent.precision(.fractionLength(0))) win rate").font(PlayboardTypography.body()).foregroundStyle(palette.textMuted) } } } }
    private var nameEditor: some View { PlayboardCard { VStack(alignment: .leading, spacing: PlayboardSpacing.small) { Text("Your name").font(PlayboardTypography.label()).foregroundStyle(palette.textPrimary); TextField("Display name", text: $viewModel.state.name).textFieldStyle(.roundedBorder).accessibilityIdentifier("profile-name-field"); Button(viewModel.state.isSavingName ? "Saving…" : "Save name") { viewModel.saveName() }.disabled(viewModel.state.isSavingName).accessibilityIdentifier("save-profile-name"); if let message = viewModel.state.saveMessage { Text(message).font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted) } } } }
    private func statGrid(_ stats: PlayerStats) -> some View { LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: PlayboardSpacing.medium) { stat("Wins", "\(stats.wins)"); stat("Losses", "\(stats.losses)"); stat("Points for", "\(stats.pointsFor)"); stat("Points against", "\(stats.pointsAgainst)"); stat("Current streak", "\(stats.currentStreak)"); stat("Best streak", "\(stats.bestStreak)") } }
    private func stat(_ title: String, _ value: String) -> some View { PlayboardCard { VStack(alignment: .leading, spacing: 4) { Text(value).font(PlayboardTypography.title()).foregroundStyle(palette.brand); Text(title).font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted) } } }
    private func finishes(_ stats: PlayerStats) -> some View { PlayboardCard { VStack(alignment: .leading, spacing: PlayboardSpacing.small) { Text("Monthly finishes").font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary); if stats.monthlyFinishes.isEmpty { Text("Finishing positions are recorded after each month closes.").font(PlayboardTypography.body()).foregroundStyle(palette.textMuted) } else { ForEach(stats.monthlyFinishes) { finish in HStack { Text(finish.month); Spacer(); Text(finish.rank.map { "#\($0) / \(finish.qualifiedPlayers)" } ?? "—") }.font(PlayboardTypography.body()).foregroundStyle(palette.textPrimary).accessibilityLabel("\(finish.month), \(finish.rank.map(String.init) ?? "no finish")") } } } } }
    private var partners: some View { PlayboardCard { VStack(alignment: .leading, spacing: PlayboardSpacing.small) { Button { viewModel.togglePartners() } label: { Label("Partners", systemImage: viewModel.state.partnersExpanded ? "chevron.up" : "chevron.down").font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary) }.accessibilityIdentifier("profile-partners-toggle"); if viewModel.state.partnersExpanded { if viewModel.state.isLoadingPartners { ProgressView() } else if let error = viewModel.state.partnersError { Text(error).foregroundStyle(palette.textMuted) } else if viewModel.state.partners.isEmpty { Text("No partners yet.").foregroundStyle(palette.textMuted) } else { ForEach(viewModel.state.partners) { partner in HStack { Text(partner.displayName); Spacer(); Text("\(partner.winsTogether)/\(partner.gamesTogether) · \(partner.winRate, format: .percent.precision(.fractionLength(0)))") }.font(PlayboardTypography.body()).foregroundStyle(palette.textPrimary) } } } } } }
    private var account: some View { PlayboardCard { VStack(alignment: .leading, spacing: PlayboardSpacing.small) { Text("Signed in with Google").font(PlayboardTypography.label()).foregroundStyle(palette.textPrimary); Text(email).font(PlayboardTypography.body()).foregroundStyle(palette.textMuted); Button(role: .destructive, action: signOut) { Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right").frame(maxWidth: .infinity, minHeight: 44) }.buttonStyle(.bordered).accessibilityIdentifier("sign-out-button") } } }
}
