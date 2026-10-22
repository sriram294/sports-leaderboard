import Foundation
import SwiftUI

/// Paginated, locally grouped match history for the active group.
struct MatchesScreen: View {
    @ObservedObject var viewModel: MatchesViewModel
    /// Opens the Add tab pre-filled to edit the given match.
    var onEdit: (String) -> Void = { _ in }
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
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("matches-screen")
        .alert("Delete this match?", isPresented: deleteAlertBinding) {
            Button("Cancel", role: .cancel) { viewModel.cancelDelete() }
            Button("Delete", role: .destructive) { Task { await viewModel.confirmDelete() } }
        } message: {
            Text("Its result is removed from the leaderboard and everyone's stats. This cannot be undone.")
        }
    }

    private var deleteAlertBinding: Binding<Bool> {
        Binding(get: { viewModel.state.pendingDeletionID != nil }, set: { if !$0 { viewModel.cancelDelete() } })
    }

    private var history: some View {
        ScrollView {
            LazyVStack(spacing: PlayboardSpacing.medium) {
                header
                if let error = viewModel.state.deleteErrorMessage {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .font(PlayboardTypography.body())
                        .foregroundStyle(palette.statLoss)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
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
                        MatchDayView(section: section, viewModel: viewModel, onEdit: onEdit)
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
    let onEdit: (String) -> Void
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        let expanded = viewModel.isDayExpanded(section.day)
        Section {
            if expanded {
                ForEach(section.matches) { match in
                    let isOpen = viewModel.state.expandedMatchID == match.id
                    MatchHistoryCard(
                        match: match,
                        isExpanded: isOpen,
                        detail: isOpen ? viewModel.state.detail : nil,
                        isDetailLoading: isOpen && viewModel.state.isDetailLoading,
                        detailError: isOpen ? viewModel.state.detailErrorMessage : nil,
                        canModify: isOpen && viewModel.state.detail.map(viewModel.state.canModify) == true,
                        isDeleting: viewModel.state.isDeleting && viewModel.state.pendingDeletionID == match.id,
                        action: { viewModel.toggleDetail(matchID: match.id) },
                        onEdit: { onEdit(match.id) },
                        onDelete: { viewModel.requestDelete(matchID: match.id) }
                    )
                }
            }
        } header: {
            Button { viewModel.toggleDay(section.day) } label: {
                HStack {
                    Text("\(section.day.formatted(date: .abbreviated, time: .omitted)) · \(section.matches.count) \(section.matches.count == 1 ? "match" : "matches")")
                        .font(PlayboardTypography.eyebrow())
                        .foregroundStyle(palette.textMuted)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.caption)
                        .foregroundStyle(palette.textMuted)
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(expanded ? "Expanded" : "Collapsed")
            .accessibilityHint("Shows or hides this day's matches")
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
    let canModify: Bool
    let isDeleting: Bool
    let action: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void
    @Environment(\.playboardPalette) private var palette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        PlayboardCard {
            VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
                summaryButton
                if isExpanded {
                    Divider().overlay(palette.textMuted.opacity(0.18))
                    expandedContent
                }
                if isExpanded && canModify {
                    HStack(spacing: PlayboardSpacing.medium) {
                        Button(action: onEdit) {
                            Label("Edit match", systemImage: "pencil").frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("edit-match-\(match.id)")
                        Button(role: .destructive, action: onDelete) {
                            Group {
                                if isDeleting { ProgressView() } else { Label("Delete", systemImage: "trash") }
                            }
                            .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.bordered)
                        .disabled(isDeleting)
                        .accessibilityIdentifier("delete-match-\(match.id)")
                    }
                }
            }
        }
    }

    private var summaryButton: some View {
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
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint(isExpanded ? "Collapses match details" : "Expands game breakdown and history")
        .accessibilityIdentifier("match-card-\(match.id)")
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
                        size: 34,
                        previewsOnTap: false
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
                        Text("\(event.displayName) · \(eventLabel(event.action)) · \(event.createdAt.formatted(date: .abbreviated, time: .shortened))")
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

/// Audit-log copy for one match event.
private func eventLabel(_ action: String) -> String {
    switch action {
    case "created": "Recorded this match"
    case "edited", "updated": "Edited this match"
    default: action.capitalized
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
        if delta > 0 { return "\(player.displayName), rating increased by \(twoDecimals(delta))" }
        if delta < 0 { return "\(player.displayName), rating decreased by \(twoDecimals(-delta))" }
        return "\(player.displayName), rating unchanged at 0.00"
    }
}

private let ratingLocale = Locale(identifier: "en_US_POSIX")

private func twoDecimals(_ value: Double) -> String {
    String(format: "%.2f", locale: ratingLocale, value)
}

private func ratingDeltaText(_ delta: Double?) -> String {
    guard let delta else { return "Not rated" }
    if delta > 0 { return "+\(twoDecimals(delta))" }
    if delta < 0 { return "−\(twoDecimals(-delta))" }
    return "0.00"
}

private extension Color {
    init(matchHex value: String) {
        let cleaned = value.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        self = UInt32(cleaned, radix: 16).map(Color.init(hex:)) ?? .gray
    }
}
