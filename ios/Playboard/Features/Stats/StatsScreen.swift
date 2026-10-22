import SwiftUI

/// Group Insights dashboard: records, biggest win, monthly winners, and partner lookups.
struct StatsScreen: View {
    @ObservedObject var viewModel: StatsViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        Group {
            if viewModel.state.groupID == nil {
                PlayboardEmptyView(title: "No active group", message: "Create or join a group to see its insights.")
                    .padding(PlayboardSpacing.extraLarge)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: PlayboardSpacing.large) {
                        header
                        content
                    }
                    .padding(.horizontal, PlayboardSpacing.large)
                    .padding(.bottom, PlayboardSpacing.section)
                }
                .refreshable { await viewModel.refresh() }
            }
        }
        .accessibilityIdentifier("stats-screen")
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
            VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                PlayboardSectionLabel("Group insights")
                Text("Stats").font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary)
            }
            Picker("Time range", selection: Binding(get: { viewModel.state.range }, set: { viewModel.setRange($0) })) {
                ForEach(StatsTimeRange.allCases, id: \.self) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("stats-range")
        }
        .padding(.top, PlayboardSpacing.medium)
    }

    @ViewBuilder private var content: some View {
        let state = viewModel.state
        if state.isLoading && state.records == nil {
            PlayboardLoadingView(message: "Loading insights…")
        } else if let error = state.errorMessage, state.records == nil {
            PlayboardErrorView(message: error, retry: viewModel.retry)
        } else if !state.hasMatches {
            PlayboardEmptyView(
                title: "No matches yet",
                message: state.range == .month ? "Record a match this month to see insights." : "Record a match to see insights."
            )
            if !state.monthlyWinners.isEmpty { MonthlyWinnersCard(winners: state.monthlyWinners) }
        } else {
            if let records = state.records { RecordsCard(records: records) }
            BiggestWinCard(biggestWin: state.biggestWin)
            if !state.monthlyWinners.isEmpty { MonthlyWinnersCard(winners: state.monthlyWinners) }
            StatsPartnersCard(viewModel: viewModel)
        }
    }
}

private struct RecordsCard: View {
    let records: StatsRecords
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            VStack(alignment: .leading, spacing: PlayboardSpacing.small) {
                PlayboardSectionLabel("Records")
                row("Total matches", value: "\(records.totalMatches)", player: nil)
                if let leader = records.winLeader {
                    row("Win leader", value: leader.winRate.formatted(.percent.precision(.fractionLength(0))), player: leader)
                }
                if let entry = records.mostPoints { row("Most points", value: "\(entry.pointsFor)", player: entry) }
                if let entry = records.mostActive { row("Most active", value: "\(entry.gamesPlayed) games", player: entry) }
                if let entry = records.longestStreak { row("Longest streak", value: "\(entry.bestStreak) wins", player: entry) }
                if let entry = records.currentStreak { row("On fire", value: "\(entry.currentStreak) in a row", player: entry) }
            }
        }
    }

    private func row(_ title: String, value: String, player: LeaderboardEntry?) -> some View {
        HStack(spacing: PlayboardSpacing.medium) {
            if let player {
                PlayerAvatar(displayName: player.displayName, avatarID: player.avatarID, photoURL: player.photoURL,
                             color: Color(apiHex: player.avatarColor), size: 32, previewsOnTap: false)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                if let player {
                    Text(player.displayName).font(PlayboardTypography.label()).foregroundStyle(palette.textPrimary).lineLimit(1)
                }
            }
            Spacer()
            Text(value).font(PlayboardTypography.label()).monospacedDigit().foregroundStyle(palette.brand)
        }
        .frame(minHeight: 44)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([title, player?.displayName, value].compactMap { $0 }.joined(separator: ", "))
    }
}

private struct BiggestWinCard: View {
    let biggestWin: BiggestWin?
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            VStack(alignment: .leading, spacing: PlayboardSpacing.small) {
                PlayboardSectionLabel("Biggest win · recent matches")
                if let biggestWin {
                    Text("\(biggestWin.margin) point margin").font(PlayboardTypography.title()).foregroundStyle(palette.brand)
                    ForEach(biggestWin.match.teams.sorted { $0.teamNo < $1.teamNo }) { team in
                        HStack {
                            Text(team.players.map(\.displayName).joined(separator: " & "))
                                .font(PlayboardTypography.label())
                                .foregroundStyle(team.isWinner ? palette.brand : palette.textPrimary)
                            if team.isWinner {
                                Image(systemName: "trophy.fill").foregroundStyle(palette.brand).accessibilityLabel("Winner")
                            }
                        }
                    }
                    Text(biggestWin.match.sets.sorted { $0.setNo < $1.setNo }.map { "\($0.team1Score)–\($0.team2Score)" }.joined(separator: ", "))
                        .font(PlayboardTypography.body()).monospacedDigit().foregroundStyle(palette.textPrimary)
                    Text(biggestWin.match.playedAt.formatted(date: .abbreviated, time: .omitted))
                        .font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                } else {
                    Text("No decisive matches yet.").font(PlayboardTypography.body()).foregroundStyle(palette.textMuted)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct MonthlyWinnersCard: View {
    let winners: [MonthlyTrophy]
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
                PlayboardSectionLabel("Monthly winners")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: PlayboardSpacing.medium)], spacing: PlayboardSpacing.medium) {
                    ForEach(winners) { winner in
                        VStack(spacing: PlayboardSpacing.extraSmall) {
                            ZStack(alignment: .top) {
                                PlayerAvatar(displayName: winner.displayName, avatarID: winner.avatarID, photoURL: winner.photoURL,
                                             color: Color(apiHex: winner.avatarColor), size: 52, previewsOnTap: false)
                                    .padding(.top, 14)
                                Image("Crown").resizable().scaledToFit().frame(width: 26, height: 26).accessibilityHidden(true)
                            }
                            Text(winner.displayName).font(PlayboardTypography.label()).foregroundStyle(palette.textPrimary).lineLimit(1)
                            Text(winner.shortMonthLabel).font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                        }
                        .frame(maxWidth: .infinity)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("\(winner.longMonthLabel), \(winner.displayName)")
                    }
                }
            }
        }
    }
}

private struct StatsPartnersCard: View {
    @ObservedObject var viewModel: StatsViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            VStack(alignment: .leading, spacing: PlayboardSpacing.small) {
                Button { viewModel.togglePartners() } label: {
                    HStack {
                        Text("PARTNERS").font(PlayboardTypography.eyebrow()).tracking(1.2).foregroundStyle(palette.textMuted)
                        Spacer()
                        Image(systemName: viewModel.state.partnersExpanded ? "chevron.up" : "chevron.down").foregroundStyle(palette.textMuted)
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityValue(viewModel.state.partnersExpanded ? "Expanded" : "Collapsed")
                .accessibilityIdentifier("stats-partners-toggle")
                if viewModel.state.partnersExpanded {
                    if viewModel.state.players.isEmpty {
                        Text("Nobody has played in this period yet.").font(PlayboardTypography.body()).foregroundStyle(palette.textMuted)
                    } else {
                        Picker("Player", selection: Binding(
                            get: { viewModel.state.selectedPlayerID ?? "" },
                            set: { viewModel.selectPlayer($0) }
                        )) {
                            ForEach(viewModel.state.players) { Text($0.displayName).tag($0.userID) }
                        }
                        .pickerStyle(.menu)
                        .accessibilityIdentifier("stats-partner-player")
                        PartnerList(
                            partners: viewModel.state.partners,
                            isLoading: viewModel.state.isLoadingPartners,
                            error: viewModel.state.partnersError
                        )
                    }
                }
            }
        }
    }
}
