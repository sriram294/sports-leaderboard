import SwiftUI
import UIKit

/// How many ranked players the shared leaderboard image includes.
let maxShareRows = 10

/// Rows for the shared image: ranked players only, in canonical order, capped at ``maxShareRows``.
func shareRankings(_ rankings: [LeaderboardEntry]) -> [LeaderboardEntry] {
    Array(rankings.filter { !$0.provisional }.prefix(maxShareRows))
}

/// Fixed-width, brand-styled leaderboard rendered to an image for sharing.
struct LeaderboardShareCard: View {
    let groupName: String
    let period: String
    let entries: [LeaderboardEntry]
    let date: Date
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: PlayboardSpacing.large) {
            VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                AppWordmark(logoHeight: 26, fontSize: 20)
                Text(groupName).font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary)
                Text("\(period) leaderboard · \(date.formatted(.dateTime.day().month(.abbreviated).year()))")
                    .font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
            }
            VStack(spacing: 0) {
                ForEach(entries) { entry in
                    HStack(spacing: PlayboardSpacing.medium) {
                        Text("\(entry.rank)")
                            .font(PlayboardTypography.title()).monospacedDigit()
                            .foregroundStyle(entry.rank == 1 ? palette.brand : palette.textPrimary)
                            .frame(width: 30, alignment: .leading)
                        PlayerAvatar(displayName: entry.displayName, avatarID: entry.avatarID, photoURL: nil,
                                     color: Color(apiHex: entry.avatarColor), size: 36, previewsOnTap: false)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(entry.displayName).font(PlayboardTypography.label()).foregroundStyle(palette.textPrimary).lineLimit(1)
                            Text("\(entry.wins)W–\(entry.losses)L · \(entry.gamesPlayed) games")
                                .font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                        }
                        Spacer()
                        Text(metric(entry)).font(PlayboardTypography.label()).monospacedDigit().foregroundStyle(palette.brand)
                    }
                    .padding(.vertical, PlayboardSpacing.small)
                    if entry.id != entries.last?.id {
                        Divider().overlay(palette.textMuted.opacity(0.18))
                    }
                }
            }
            .padding(PlayboardSpacing.large)
            .background(palette.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .padding(PlayboardSpacing.extraLarge)
        .frame(width: 460)
        .background(palette.background)
    }

    private func metric(_ entry: LeaderboardEntry) -> String {
        entry.rating.map { "\($0.formatted(.number.precision(.fractionLength(1)))) rating" }
            ?? "\(entry.winRate.formatted(.percent.precision(.fractionLength(0)))) win rate"
    }
}

/// Renders ``LeaderboardShareCard`` to a bitmap in the app's current appearance.
@MainActor
enum LeaderboardShareRenderer {
    static func image(groupName: String, period: String, rankings: [LeaderboardEntry], palette: PlayboardPalette) -> UIImage? {
        let card = LeaderboardShareCard(groupName: groupName, period: period, entries: shareRankings(rankings), date: Date())
            .environment(\.playboardPalette, palette)
        let renderer = ImageRenderer(content: card)
        renderer.scale = 3
        return renderer.uiImage
    }
}
