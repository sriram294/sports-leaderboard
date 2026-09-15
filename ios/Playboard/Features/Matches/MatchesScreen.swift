import Foundation
import SwiftUI

/// Paginated, locally grouped match history for the active group.
struct MatchesScreen: View {
    @ObservedObject var viewModel: MatchesViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        Group {
            if viewModel.state.groupID == nil {
                PlayboardEmptyView(title: "No active group", message: "Create or join a group to browse its matches.")
                    .padding(PlayboardSpacing.extraLarge)
            } else if viewModel.state.isLoading && viewModel.state.matches.isEmpty {
                PlayboardLoadingView(message: "Loading matches")
            } else if let error = viewModel.state.errorMessage, viewModel.state.matches.isEmpty {
                PlayboardErrorView(message: error, retry: viewModel.retry)
                    .padding(PlayboardSpacing.extraLarge)
            } else {
                history
            }
        }
        .accessibilityIdentifier("matches-screen")
    }

    private var history: some View {
        ScrollView {
            LazyVStack(spacing: PlayboardSpacing.medium) {
                header
                if let stale = viewModel.state.staleMessage {
                    Label(stale, systemImage: "wifi.slash")
                        .font(PlayboardTypography.eyebrow())
                        .foregroundStyle(palette.textMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if viewModel.state.matches.isEmpty {
                    PlayboardEmptyView(
                        title: viewModel.state.mineOnly ? "No matches for you" : "No matches yet",
                        message: viewModel.state.mineOnly
                            ? "Matches you play in will appear here."
                            : "Record the first result from the Add tab."
                    )
                    .accessibilityIdentifier("matches-empty")
                } else {
                    ForEach(viewModel.sections()) { section in
                        MatchDayView(section: section, viewModel: viewModel)
                    }
                    if viewModel.state.nextCursor != nil {
                        Button {
                            viewModel.loadMore()
                        } label: {
                            if viewModel.state.isLoadingMore {
                                ProgressView().frame(maxWidth: .infinity, minHeight: 44)
                            } else {
                                Label("Load older matches", systemImage: "clock.arrow.circlepath")
                                    .frame(maxWidth: .infinity, minHeight: 44)
                            }
                        }
                        .buttonStyle(.bordered)
                        .disabled(!viewModel.state.canLoadMore)
                    }
                }
            }
            .padding(.horizontal, PlayboardSpacing.large)
            .padding(.bottom, PlayboardSpacing.section)
        }
        .refreshable { await viewModel.refresh() }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                Text("MATCH HISTORY")
                    .font(PlayboardTypography.eyebrow())
                    .tracking(1.2)
                    .foregroundStyle(palette.textMuted)
                Text("\(viewModel.state.matches.count) loaded")
                    .font(PlayboardTypography.title())
                    .foregroundStyle(palette.textPrimary)
            }
            Spacer()
            Button {
                viewModel.toggleMineOnly()
            } label: {
                Label("My matches", systemImage: viewModel.state.mineOnly ? "checkmark.circle.fill" : "circle")
                    .font(PlayboardTypography.label())
                    .frame(minHeight: 44)
            }
            .buttonStyle(.bordered)
            .accessibilityValue(viewModel.state.mineOnly ? "On" : "Off")
            .accessibilityIdentifier("matches-mine-filter")
        }
        .padding(.top, PlayboardSpacing.medium)
    }
}

private struct MatchDayView: View {
    let section: MatchDaySection
    @ObservedObject var viewModel: MatchesViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        Section {
            ForEach(section.matches) { match in
                MatchHistoryCard(
                    match: match,
                    isExpanded: viewModel.state.expandedMatchID == match.id,
                    detail: viewModel.state.expandedMatchID == match.id ? viewModel.state.detail : nil,
                    isDetailLoading: viewModel.state.expandedMatchID == match.id && viewModel.state.isDetailLoading,
                    detailError: viewModel.state.expandedMatchID == match.id ? viewModel.state.detailErrorMessage : nil,
                    action: { viewModel.toggleDetail(matchID: match.id) }
                )
            }
        } header: {
            Text("\(section.day.formatted(date: .abbreviated, time: .omitted)) · \(section.matches.count) \(section.matches.count == 1 ? "match" : "matches")")
                .font(PlayboardTypography.eyebrow())
                .foregroundStyle(palette.textMuted)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, PlayboardSpacing.small)
        }
    }
}

private struct MatchHistoryCard: View {
    let match: MatchSummary
    let isExpanded: Bool
    let detail: MatchDetail?
    let isDetailLoading: Bool
    let detailError: String?
    let action: () -> Void
    @Environment(\.playboardPalette) private var palette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        PlayboardCard {
            Button(action: action) {
                VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
                    if dynamicTypeSize.isAccessibilitySize {
                        VStack(alignment: .leading, spacing: PlayboardSpacing.small) { teams; scores }
                    } else {
                        HStack(alignment: .center, spacing: PlayboardSpacing.medium) {
                            teams
                            Spacer(minLength: 0)
                            scores
                        }
                    }
                    if isExpanded {
                        Divider().overlay(palette.textMuted.opacity(0.18))
                        expandedContent
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(isExpanded ? "Collapses match details" : "Expands game breakdown and history")
            .accessibilityIdentifier("match-card-\(match.id)")
        }
    }

    private var teams: some View {
        VStack(alignment: .leading, spacing: PlayboardSpacing.small) {
            teamRow(match.team(1))
            teamRow(match.team(2))
        }
    }

    private func teamRow(_ team: MatchTeam?) -> some View {
        HStack(spacing: PlayboardSpacing.small) {
            if let team {
                ForEach(team.players) { player in
                    PlayerAvatar(
                        displayName: player.displayName,
                        avatarID: player.avatarID,
                        photoURL: player.photoURL,
                        color: Color(matchHex: player.avatarColor),
                        size: 34
                    )
                }
                Text(team.players.map(\.displayName).joined(separator: " & "))
                    .font(PlayboardTypography.label())
                    .foregroundStyle(team.isWinner ? palette.brand : palette.textPrimary)
                    .lineLimit(2)
                if team.isWinner {
                    Text("W").font(PlayboardTypography.eyebrow()).foregroundStyle(palette.onBrand)
                        .padding(6).background(palette.brand, in: Circle())
                        .accessibilityLabel("Winner")
                }
            }
        }
    }

    private var scores: some View {
        HStack(spacing: PlayboardSpacing.small) {
            VStack(alignment: .trailing, spacing: PlayboardSpacing.extraSmall) {
                ForEach(match.sets.sorted { $0.setNo < $1.setNo }) { set in
                    Text("\(set.team1Score)–\(set.team2Score)")
                        .font(PlayboardTypography.label()).monospacedDigit().foregroundStyle(palette.textPrimary)
                }
            }
            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                .foregroundStyle(palette.textMuted)
        }
    }

    @ViewBuilder
    private var expandedContent: some View {
        if isDetailLoading {
            ProgressView("Loading details").frame(maxWidth: .infinity, minHeight: 70)
        } else if let detailError {
            Label(detailError, systemImage: "exclamationmark.triangle")
                .font(PlayboardTypography.body()).foregroundStyle(palette.textMuted)
        } else if let detail {
            VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
                Text("GAME BREAKDOWN").font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                ForEach(detail.sets.sorted { $0.setNo < $1.setNo }) { set in
                    Text("Set \(set.setNo):  \(set.team1Score)–\(set.team2Score)")
                        .font(PlayboardTypography.body()).monospacedDigit().foregroundStyle(palette.textPrimary)
                }
                if let winner = detail.winningTeam {
                    Text("Winner: \(winner.players.map(\.displayName).joined(separator: " & "))")
                        .font(PlayboardTypography.label()).foregroundStyle(palette.brand)
                }
                if let changes = detail.ratingChanges {
                    ratingChanges(changes, detail: detail)
                }
                Text("HISTORY").font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                ForEach(detail.events) { event in
                    Label {
                        Text("\(event.displayName) · \(event.action.capitalized) · \(event.createdAt.formatted(date: .abbreviated, time: .shortened))")
                    } icon: {
                        Image(systemName: event.action == "created" ? "plus.circle" : "pencil.circle")
                    }
                    .font(PlayboardTypography.body()).foregroundStyle(palette.textMuted)
                }
            }
        }
    }

    private func ratingChanges(_ changes: [MatchRatingChange], detail: MatchDetail) -> some View {
        let byUser = Dictionary(uniqueKeysWithValues: changes.map { ($0.userID, $0) })
        return VStack(alignment: .leading, spacing: PlayboardSpacing.small) {
            Text("RATING CHANGE").font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
            ForEach(detail.teams.sorted { $0.teamNo < $1.teamNo }) { team in
                ForEach(team.players) { player in
                    if let change = byUser[player.userID] {
                        RatingChangeRow(player: player, delta: change.ratingDelta)
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
    }
}

private struct RatingChangeRow: View {
    let player: MatchPlayer
    let delta: Double?
    @Environment(\.playboardPalette) private var palette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                    identity
                    value.frame(maxWidth: .infinity, alignment: .trailing)
                }
            } else {
                HStack(spacing: PlayboardSpacing.small) {
                    identity
                    Spacer(minLength: PlayboardSpacing.small)
                    value
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private var identity: some View {
        HStack(spacing: PlayboardSpacing.small) {
            PlayerAvatar(
                displayName: player.displayName,
                avatarID: player.avatarID,
                photoURL: player.photoURL,
                color: Color(matchHex: player.avatarColor),
                size: 28
            )
            Text(player.displayName).font(PlayboardTypography.body()).foregroundStyle(palette.textPrimary)
        }
    }

    private var value: some View {
        Text(ratingDeltaText(delta))
            .font(PlayboardTypography.label())
            .monospacedDigit()
            .foregroundStyle(delta.map { $0 > 0 ? palette.statWin : $0 < 0 ? palette.statLoss : palette.textMuted } ?? palette.textMuted)
    }

    private var accessibilityText: String {
        guard let delta else { return "\(player.displayName), not rated" }
        if delta > 0 { return "\(player.displayName), rating increased by \(oneDecimal(delta))" }
        if delta < 0 { return "\(player.displayName), rating decreased by \(oneDecimal(-delta))" }
        return "\(player.displayName), rating unchanged at 0.0"
    }
}

private let ratingLocale = Locale(identifier: "en_US_POSIX")

private func oneDecimal(_ value: Double) -> String {
    String(format: "%.1f", locale: ratingLocale, value)
}

private func ratingDeltaText(_ delta: Double?) -> String {
    guard let delta else { return "Not rated" }
    if delta > 0 { return "+\(oneDecimal(delta))" }
    if delta < 0 { return "−\(oneDecimal(-delta))" }
    return "0.0"
}

private extension Color {
    init(matchHex value: String) {
        let cleaned = value.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        self = UInt32(cleaned, radix: 16).map(Color.init(hex:)) ?? .gray
    }
}
