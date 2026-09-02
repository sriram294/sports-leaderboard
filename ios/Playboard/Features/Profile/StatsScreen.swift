import SwiftUI

/// Lightweight group insights dashboard derived from the canonical Board and Matches state.
struct StatsScreen: View {
    @ObservedObject var boardViewModel: BoardViewModel
    @ObservedObject var matchesViewModel: MatchesViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: PlayboardSpacing.large) {
            Text("Insights").font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary)
            if boardViewModel.state.isLoading || matchesViewModel.state.isLoading { PlayboardLoadingView(message: "Loading insights…") }
            else if boardViewModel.state.rankings.isEmpty && matchesViewModel.state.matches.isEmpty { PlayboardEmptyView(title: "Play some matches to see insights", message: "Records and biggest wins appear here after your first match.") }
            else { records; biggestWin }
        }.padding(PlayboardSpacing.extraLarge) }
        .accessibilityIdentifier("stats-screen")
    }

    private var records: some View { VStack(alignment: .leading, spacing: PlayboardSpacing.small) { Text("Records").font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary); if let leader = boardViewModel.state.rankings.first { insight("Win leader", leader.displayName); insight("Most points", boardViewModel.state.rankings.max(by: { $0.pointsFor < $1.pointsFor })?.displayName ?? leader.displayName); insight("Most active", boardViewModel.state.rankings.max(by: { $0.gamesPlayed < $1.gamesPlayed })?.displayName ?? leader.displayName) }; insight("Total matches", "\(matchesViewModel.state.groupMatchCount)") }.accessibilityElement(children: .contain) }
    private var biggestWin: some View { PlayboardCard { VStack(alignment: .leading, spacing: PlayboardSpacing.small) { Text("Biggest win").font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary); if let match = matchesViewModel.state.matches.max(by: margin) { Text("\(match.sets.reduce(0) { $0 + $1.team1Score } - match.sets.reduce(0) { $0 + $1.team2Score }) point margin").font(PlayboardTypography.body()).foregroundStyle(palette.brand); Text("Recent match window").font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted) } else { Text("No completed matches yet.").foregroundStyle(palette.textMuted) } } } }
    private func insight(_ title: String, _ value: String) -> some View { HStack { Text(title).foregroundStyle(palette.textMuted); Spacer(); Text(value).foregroundStyle(palette.textPrimary).fontWeight(.semibold) }.padding(.vertical, PlayboardSpacing.small) }
    private func margin(_ left: MatchSummary, _ right: MatchSummary) -> Bool { scoreMargin(left) < scoreMargin(right) }
    private func scoreMargin(_ match: MatchSummary) -> Int { abs(match.sets.reduce(0) { $0 + $1.team1Score } - match.sets.reduce(0) { $0 + $1.team2Score }) }
}
