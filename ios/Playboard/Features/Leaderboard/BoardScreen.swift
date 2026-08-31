import SwiftUI

/// Branded month/all-time leaderboard for the active group.
struct BoardScreen: View {
    @ObservedObject var viewModel: BoardViewModel

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
                        BoardHeader(viewModel: viewModel)
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
                            LeaderboardPodium(entries: viewModel.state.podium, range: viewModel.state.range)
                            RankingsCard(viewModel: viewModel)
                        }
                    }
                    .padding(.horizontal, PlayboardSpacing.large)
                    .padding(.bottom, PlayboardSpacing.section)
                }
                .refreshable { await viewModel.refresh() }
                .accessibilityIdentifier("leaderboard-screen")
            }
        }
    }

    private var emptyMessage: String {
        if viewModel.state.groupMemberCount == 0 { return "Invite players to begin building a board." }
        return viewModel.state.range == .month
            ? "Record a match to start \(viewModel.state.monthName)'s standings."
            : "Rankings appear after the group's first match."
    }
}

private struct BoardHeader: View {
    @ObservedObject var viewModel: BoardViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                Text(viewModel.state.range == .month ? "\(viewModel.state.monthName.uppercased()) STANDINGS" : "ALL-TIME STANDINGS")
                    .font(PlayboardTypography.eyebrow())
                    .tracking(1.2)
                    .foregroundStyle(palette.textMuted)
                Text("Top players")
                    .font(PlayboardTypography.title())
                    .foregroundStyle(palette.textPrimary)
            }
            Spacer()
            Menu {
                ForEach(LeaderboardRange.allCases) { range in
                    Button {
                        viewModel.setRange(range)
                    } label: {
                        Label(range.rawValue, systemImage: range == viewModel.state.range ? "checkmark" : "calendar")
                    }
                }
            } label: {
                Label(viewModel.state.range.rawValue, systemImage: "chevron.down")
                    .labelStyle(.titleAndIcon)
                    .font(PlayboardTypography.label())
                    .frame(minHeight: 44)
            }
            .accessibilityIdentifier("leaderboard-range")
        }
        .padding(.top, PlayboardSpacing.medium)
    }
}

private struct LeaderboardPodium: View {
    let entries: [LeaderboardEntry]
    let range: LeaderboardRange
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(spacing: PlayboardSpacing.small) {
                ForEach(entries) { PodiumSlot(entry: $0, champion: $0.rank == entries.first?.rank, range: range) }
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
            PodiumSlot(entry: entries[index], champion: champion, range: range)
                .frame(maxWidth: .infinity)
        } else {
            Color.clear.frame(maxWidth: .infinity, minHeight: 100).accessibilityHidden(true)
        }
    }
}

private struct PodiumSlot: View {
    let entry: LeaderboardEntry
    let champion: Bool
    let range: LeaderboardRange
    @Environment(\.playboardPalette) private var palette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(spacing: PlayboardSpacing.small) {
            if champion && !dynamicTypeSize.isAccessibilitySize {
                Image("PodiumCrown").resizable().scaledToFit().frame(height: 36).accessibilityHidden(true)
            }
            PlayerAvatar(
                displayName: entry.displayName,
                avatarID: entry.avatarID,
                photoURL: entry.photoURL,
                color: playerColor,
                size: champion && !dynamicTypeSize.isAccessibilitySize ? 82 : 58
            )
            Text(dynamicTypeSize.isAccessibilitySize ? "#\(entry.rank)  \(entry.displayName)" : entry.displayName)
                .font(PlayboardTypography.label())
                .foregroundStyle(palette.textPrimary)
                .lineLimit(dynamicTypeSize.isAccessibilitySize ? 2 : 1)
            Text("\(entry.rating, specifier: "%.1f") rating")
                .font(PlayboardTypography.eyebrow())
                .monospacedDigit()
                .foregroundStyle(champion ? playerColor : palette.textMuted)
        }
        .padding(.horizontal, PlayboardSpacing.extraSmall)
        .padding(.vertical, PlayboardSpacing.medium)
        .frame(maxWidth: .infinity, minHeight: champion && !dynamicTypeSize.isAccessibilitySize ? 180 : 140)
        .background {
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
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(playerColor.opacity(champion ? 0.45 : 0.2)))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Rank \(entry.rank), \(entry.displayName)")
        .accessibilityValue("\(entry.rating.formatted(.number.precision(.fractionLength(1)))) rating, \(entry.wins) wins from \(entry.gamesPlayed) games, \(range.rawValue)")
    }

    private var playerColor: Color { Color(leaderboardHex: entry.avatarColor) }
}

private struct RankingsCard: View {
    @ObservedObject var viewModel: BoardViewModel
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
                ForEach(Array(viewModel.state.tableRows.enumerated()), id: \.element.id) { index, entry in
                    if index > 0 { Divider().overlay(palette.textMuted.opacity(entry.provisional && !viewModel.state.tableRows[index - 1].provisional ? 0.42 : 0.16)) }
                    LeaderboardRowView(entry: entry, minGamesToRank: viewModel.state.minGamesToRank, metric: viewModel.state.metric)
                }
            }
        }
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
            PlayerAvatar(displayName: entry.displayName, avatarID: entry.avatarID, photoURL: entry.photoURL, color: Color(leaderboardHex: entry.avatarColor), size: 38)
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
        .accessibilityValue("\(entry.wins) wins, \(entry.losses) losses, \(entry.gamesPlayed) games, rating \(entry.rating.formatted(.number.precision(.fractionLength(1)))), recent form \(formSummary)")
    }

    private var secondaryLine: String {
        if entry.provisional { return "\(max(0, minGamesToRank - entry.gamesPlayed)) more to rank · \(entry.gamesPlayed) played" }
        return "\(entry.wins)W–\(entry.losses)L · \(entry.pointsDifference >= 0 ? "+" : "")\(entry.pointsDifference) points"
    }

    private var metricValue: String {
        if entry.provisional { return "Provisional" }
        return switch metric {
        case .rating: entry.rating.formatted(.number.precision(.fractionLength(1)))
        case .winRate: entry.winRate.formatted(.percent.precision(.fractionLength(0)))
        case .games: "\(entry.gamesPlayed)"
        case .pointsDifference: "\(entry.pointsDifference >= 0 ? "+" : "")\(entry.pointsDifference)"
        }
    }

    private var metricColor: Color {
        if entry.provisional { return palette.textMuted }
        return switch metric {
        case .rating: entry.rating >= 40 ? palette.brand : entry.rating >= 25 ? palette.winRateMid : palette.winRateLow
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
