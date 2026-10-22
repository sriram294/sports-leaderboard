import SwiftUI
import UIKit

private func monthLabel(_ value: String) -> String {
    let input = DateFormatter()
    input.dateFormat = "yyyy-MM"
    let output = DateFormatter()
    output.dateFormat = "MMM yyyy"
    return input.date(from: value).map { output.string(from: $0) } ?? value
}

/// Branded month/all-time leaderboard for the active group.
struct BoardScreen: View {
    @ObservedObject var viewModel: BoardViewModel
    var groupName = ""
    /// Opens a player's profile from a podium slot or ranking row.
    var onPlayerTap: (LeaderboardEntry) -> Void = { _ in }
    @Environment(\.playboardPalette) private var palette
    @State private var shareImage: ShareImage?

    var body: some View {
        Group {
            if viewModel.state.groupID == nil {
                PlayboardEmptyView(title: "No active group", message: "Create or join a group to see its leaderboard.")
                    .padding(PlayboardSpacing.extraLarge)
            } else if viewModel.state.isLoading && viewModel.state.rankings.isEmpty {
                BoardLoadingSkeleton()
            } else if let error = viewModel.state.errorMessage, viewModel.state.rankings.isEmpty {
                PlayboardErrorView(message: error, retry: viewModel.retry)
                    .padding(PlayboardSpacing.extraLarge)
            } else {
                ScrollView {
                    LazyVStack(spacing: PlayboardSpacing.large) {
                        BoardHeader(viewModel: viewModel, onShare: share)
                        if let stale = viewModel.state.staleMessage {
                            Label(stale, systemImage: "wifi.slash")
                                .font(PlayboardTypography.eyebrow())
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .accessibilityIdentifier("leaderboard-stale-message")
                        }
                        if viewModel.state.rankings.isEmpty {
                            PlayboardEmptyView(
                                title: viewModel.state.groupMemberCount == 0 ? "No players yet" : "No matches yet",
                                message: emptyMessage
                            )
                            .accessibilityIdentifier("leaderboard-empty")
                        } else {
                            LeaderboardPodium(entries: viewModel.state.podium, period: period, onPlayerTap: onPlayerTap)
                            RankingsCard(viewModel: viewModel, onPlayerTap: onPlayerTap)
                        }
                    }
                    .padding(.horizontal, PlayboardSpacing.large)
                    .padding(.bottom, PlayboardSpacing.section)
                }
                .refreshable { await viewModel.refresh() }
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("leaderboard-screen")
            }
        }
        .sheet(item: $shareImage) { ActivityShareSheet(items: [$0.image]).ignoresSafeArea() }
    }

    private var period: String { viewModel.state.selectedSnapshotMonth.map(monthLabel) ?? "This Month" }

    private func share() {
        guard let image = LeaderboardShareRenderer.image(
            groupName: groupName, period: period, rankings: viewModel.state.rankings, palette: palette
        ) else { return }
        shareImage = ShareImage(image: image)
    }

    private var emptyMessage: String {
        if viewModel.state.groupMemberCount == 0 { return "Invite players to begin building a board." }
        return viewModel.state.selectedSnapshotMonth == nil
            ? "Record a match to start \(viewModel.state.monthName)'s standings."
            : "No standings were saved for this month."
    }
}

/// A rendered share image, identifiable so it can drive a sheet.
private struct ShareImage: Identifiable {
    let id = UUID()
    let image: UIImage
}

private struct BoardHeader: View {
    @ObservedObject var viewModel: BoardViewModel
    let onShare: () -> Void
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                Text("\((viewModel.state.selectedSnapshotMonth.map(monthLabel) ?? viewModel.state.monthName).uppercased()) STANDINGS")
                    .font(PlayboardTypography.eyebrow())
                    .tracking(1.2)
                    .foregroundStyle(palette.textMuted)
                Text("Top players")
                    .font(PlayboardTypography.title())
                    .foregroundStyle(palette.textPrimary)
            }
            Spacer()
            Menu {
                Button { viewModel.setSnapshotMonth(nil) } label: {
                    Label("This Month", systemImage: viewModel.state.selectedSnapshotMonth == nil ? "checkmark" : "calendar")
                }
                ForEach(viewModel.state.availableSnapshotMonths, id: \.self) { month in
                    Button { viewModel.setSnapshotMonth(month) } label: {
                        Label(monthLabel(month), systemImage: month == viewModel.state.selectedSnapshotMonth ? "checkmark" : "calendar")
                    }
                }
            } label: {
                Label(viewModel.state.selectedSnapshotMonth.map(monthLabel) ?? "This Month", systemImage: "chevron.down")
                    .labelStyle(.titleAndIcon)
                    .font(PlayboardTypography.label())
                    .frame(minHeight: 44)
            }
            .accessibilityIdentifier("leaderboard-range")
            Button(action: onShare) {
                Image(systemName: "square.and.arrow.up").frame(width: 44, height: 44)
            }
            .disabled(viewModel.state.podium.isEmpty)
            .accessibilityLabel("Share leaderboard")
            .accessibilityIdentifier("leaderboard-share")
        }
        .padding(.top, PlayboardSpacing.medium)
    }
}

private struct LeaderboardPodium: View {
    let entries: [LeaderboardEntry]
    let period: String
    let onPlayerTap: (LeaderboardEntry) -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(spacing: PlayboardSpacing.small) {
                ForEach(entries) { entry in
                    Button { onPlayerTap(entry) } label: {
                        PodiumSlot(entry: entry, champion: entry.rank == entries.first?.rank, period: period)
                    }
                    .buttonStyle(.plain)
                }
            }
        } else {
            HStack(alignment: .bottom, spacing: PlayboardSpacing.small) {
                podium(at: 1)
                podium(at: 0, champion: true)
                podium(at: 2)
            }
            .frame(minHeight: 190)
        }
    }

    @ViewBuilder
    private func podium(at index: Int, champion: Bool = false) -> some View {
        if entries.indices.contains(index) {
            Button { onPlayerTap(entries[index]) } label: {
                PodiumSlot(entry: entries[index], champion: champion, period: period)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity)
        } else {
            Color.clear.frame(maxWidth: .infinity, minHeight: 100).accessibilityHidden(true)
        }
    }
}

private struct PodiumSlot: View {
    let entry: LeaderboardEntry
    let champion: Bool
    let period: String
    @Environment(\.playboardPalette) private var palette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(spacing: PlayboardSpacing.small) {
            if isLarge {
                Image("PodiumCrown").resizable().scaledToFit().frame(height: 36).accessibilityHidden(true)
            }
            PlayerAvatar(
                displayName: entry.displayName,
                avatarID: entry.avatarID,
                photoURL: entry.photoURL,
                color: playerColor,
                size: avatarSize,
                previewsOnTap: false
            )
            Text(nameText)
                .font(PlayboardTypography.label())
                .foregroundStyle(palette.textPrimary)
                .lineLimit(nameLineLimit)
            Text(ratingText)
                .font(PlayboardTypography.eyebrow())
                .monospacedDigit()
                .foregroundStyle(ratingColor)
        }
        .padding(.horizontal, PlayboardSpacing.extraSmall)
        .padding(.vertical, PlayboardSpacing.medium)
        .frame(maxWidth: .infinity, minHeight: minHeight)
        .background { slotBackground }
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(playerColor.opacity(strokeOpacity)))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabelText)
        .accessibilityHint("Opens this player's profile")
        .accessibilityValue(accessibilityValueText)
    }

    // Split out of `body` so the type checker solves small expressions instead of one large one.
    private var isLarge: Bool { champion && !dynamicTypeSize.isAccessibilitySize }
    private var avatarSize: CGFloat { isLarge ? 82 : 58 }
    private var minHeight: CGFloat { isLarge ? 180 : 140 }
    private var nameLineLimit: Int { dynamicTypeSize.isAccessibilitySize ? 2 : 1 }
    private var strokeOpacity: Double { champion ? 0.45 : 0.2 }
    private var ratingColor: Color { champion ? playerColor : palette.textMuted }

    private var nameText: String {
        dynamicTypeSize.isAccessibilitySize ? "#\(entry.rank)  \(entry.displayName)" : entry.displayName
    }

    private var ratingText: String {
        guard let rating = entry.rating else { return "Win rate" }
        return String(format: "%.1f skill rating", rating)
    }

    private var accessibilityLabelText: String { "Rank \(entry.rank), \(entry.displayName)" }

    private var accessibilityValueText: String {
        let rating: String
        if let value = entry.rating {
            rating = value.formatted(.number.precision(.fractionLength(1))) + " skill rating"
        } else {
            rating = "win rate"
        }
        return "\(rating), \(entry.wins) wins from \(entry.gamesPlayed) games, \(period)"
    }

    private var slotBackground: some View {
        RoundedRectangle(cornerRadius: 18, style: .continuous)
            .fill(palette.surface.opacity(champion ? 0.94 : 0.68))
            .overlay {
                VStack(spacing: 0) {
                    Rectangle().fill(playerColor.opacity(0.12)).frame(width: 1)
                    Rectangle().fill(playerColor.opacity(0.18)).frame(height: 1)
                }
                .padding(PlayboardSpacing.small)
                .accessibilityHidden(true)
            }
            .overlay(alignment: .bottom) {
                Rectangle().fill(playerColor).frame(height: champion ? 5 : 3)
            }
    }

    private var playerColor: Color { Color(leaderboardHex: entry.avatarColor) }
}

private struct RankingsCard: View {
    @ObservedObject var viewModel: BoardViewModel
    let onPlayerTap: (LeaderboardEntry) -> Void
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            VStack(spacing: 0) {
                HStack {
                    Text("RANKINGS").font(PlayboardTypography.eyebrow()).tracking(1.2).foregroundStyle(palette.textMuted)
                    Spacer()
                    Button {
                        viewModel.cycleMetric()
                    } label: {
                        Text("\(viewModel.state.metric.rawValue)  ▾")
                            .font(PlayboardTypography.eyebrow())
                            .foregroundStyle(palette.textPrimary)
                            .frame(minHeight: 44)
                    }
                    .accessibilityLabel("Sort rankings by \(viewModel.state.metric.rawValue)")
                    .accessibilityHint("Cycles through leaderboard metrics")
                    .accessibilityIdentifier("leaderboard-metric")
                }
                let ranked = viewModel.state.tableRows.filter { !$0.provisional }
                let provisional = viewModel.state.tableRows.filter { $0.provisional }
                Group {
                    ForEach(ranked) { entry in
                        row(entry)
                    }
                    if !provisional.isEmpty {
                        Text("Not yet ranked").font(PlayboardTypography.label()).foregroundStyle(palette.textMuted).padding(.top, PlayboardSpacing.small)
                        ForEach(provisional) { entry in
                            row(entry)
                        }
                    }
                }
            }
        }
    }
}

private extension RankingsCard {
    func row(_ entry: LeaderboardEntry) -> some View {
        Button { onPlayerTap(entry) } label: {
            LeaderboardRowView(entry: entry, minGamesToRank: viewModel.state.minGamesToRank, metric: viewModel.state.metric)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Opens this player's profile")
    }
}

private struct LeaderboardRowView: View {
    let entry: LeaderboardEntry
    let minGamesToRank: Int
    let metric: LeaderboardMetric
    @Environment(\.playboardPalette) private var palette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        HStack(alignment: dynamicTypeSize.isAccessibilitySize ? .top : .center, spacing: PlayboardSpacing.medium) {
            Text(entry.provisional ? "—" : "\(entry.rank)")
                .font(PlayboardTypography.title())
                .monospacedDigit()
                .foregroundStyle(entry.provisional ? palette.textMuted : rankColor)
                .frame(minWidth: 26, alignment: .leading)
            PlayerAvatar(displayName: entry.displayName, avatarID: entry.avatarID, photoURL: entry.photoURL, color: Color(leaderboardHex: entry.avatarColor), size: 38, previewsOnTap: false)
            VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                if dynamicTypeSize.isAccessibilitySize {
                    Text(entry.displayName).font(PlayboardTypography.label()).foregroundStyle(palette.textPrimary)
                    Text(metricValue).font(PlayboardTypography.title()).monospacedDigit().foregroundStyle(metricColor)
                } else {
                    HStack {
                        Text(entry.displayName).font(PlayboardTypography.label()).foregroundStyle(entry.provisional ? palette.textMuted : palette.textPrimary).lineLimit(1)
                        Spacer()
                        Text(metricValue).font(PlayboardTypography.label()).monospacedDigit().foregroundStyle(metricColor)
                    }
                }
                Text(secondaryLine).font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                FormDots(results: entry.recentForm)
            }
        }
        .padding(.vertical, PlayboardSpacing.small)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Rank \(entry.provisional ? "provisional" : String(entry.rank)), \(entry.displayName)")
        .accessibilityValue("\(entry.wins) wins, \(entry.losses) losses, \(entry.gamesPlayed) games, rating \(entry.rating?.formatted(.number.precision(.fractionLength(1))) ?? "win rate"), recent form \(formSummary)")
    }

    private var secondaryLine: String {
        if entry.provisional { return "\(max(0, minGamesToRank - entry.gamesPlayed)) more to rank · \(entry.gamesPlayed) played" }
        return "\(entry.wins)W–\(entry.losses)L · \(entry.pointsDifference >= 0 ? "+" : "")\(entry.pointsDifference) points"
    }

    private var metricValue: String {
        if entry.provisional { return "Provisional" }
        return switch metric {
        case .rating: entry.rating?.formatted(.number.precision(.fractionLength(1))) ?? entry.winRate.formatted(.percent.precision(.fractionLength(0)))
        case .winRate: entry.winRate.formatted(.percent.precision(.fractionLength(0)))
        case .games: "\(entry.gamesPlayed)"
        case .pointsDifference: "\(entry.pointsDifference >= 0 ? "+" : "")\(entry.pointsDifference)"
        }
    }

    private var metricColor: Color {
        if entry.provisional { return palette.textMuted }
        return switch metric {
        case .rating: (entry.rating ?? entry.winRate * 100) >= 40 ? palette.brand : (entry.rating ?? entry.winRate * 100) >= 25 ? palette.winRateMid : palette.winRateLow
        case .winRate: entry.winRate >= 0.5 ? palette.brand : entry.winRate >= 0.25 ? palette.winRateMid : palette.winRateLow
        case .games: palette.textPrimary
        case .pointsDifference: entry.pointsDifference > 0 ? palette.statWin : entry.pointsDifference < 0 ? palette.statLoss : palette.textMuted
        }
    }

    private var rankColor: Color { entry.rank == 1 ? palette.brand : entry.rank == 3 ? palette.winRateMid : palette.textPrimary }
    private var formSummary: String { entry.recentForm.map { $0 ? "win" : "loss" }.joined(separator: ", ") }
}

private struct FormDots: View {
    let results: [Bool]
    var body: some View {
        if !results.isEmpty {
            HStack(spacing: 3) {
                ForEach(Array(results.enumerated()), id: \.offset) { _, win in
                    Image(systemName: win ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .font(.system(size: 9))
                        .foregroundStyle(win ? Color.green : Color.red)
                }
            }
            .accessibilityHidden(true)
        }
    }
}

private struct BoardLoadingSkeleton: View {
    @Environment(\.playboardPalette) private var palette
    var body: some View {
        VStack(spacing: PlayboardSpacing.large) {
            RoundedRectangle(cornerRadius: 8).fill(palette.surface).frame(height: 48)
            HStack { ForEach(0..<3, id: \.self) { _ in RoundedRectangle(cornerRadius: 18).fill(palette.surface).frame(height: 160) } }
            RoundedRectangle(cornerRadius: 20).fill(palette.surface).frame(height: 300)
            Spacer()
        }
        .padding(PlayboardSpacing.large)
        .redacted(reason: .placeholder)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading leaderboard")
    }
}

private extension Color {
    init(leaderboardHex value: String) {
        let cleaned = value.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        self = UInt32(cleaned, radix: 16).map(Color.init(hex:)) ?? .gray
    }
}
