import Charts
import SwiftUI

private let finishesEmptyMessage = "Finishing positions are recorded after each month closes."

/// Month-by-month leaderboard finishes, rank 1 at the top; gap months break the line.
struct MonthlyFinishesCard: View {
    let finishes: [PlayerStats.MonthlyFinish]
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
                PlayboardSectionLabel("Monthly finishes")
                if finishes.contains(where: { $0.rank != nil }) {
                    chart
                        .frame(height: 160)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Monthly finishes")
                        .accessibilityValue(summary)
                } else {
                    Text(finishesEmptyMessage).font(PlayboardTypography.body()).foregroundStyle(palette.textMuted)
                }
            }
        }
    }

    private var lowerBound: Int { max(3, finishes.map(\.qualifiedPlayers).max() ?? 0) }

    private var chart: some View {
        Chart {
            ForEach(segments.indices, id: \.self) { segment in
                ForEach(segments[segment]) { finish in
                    if let rank = finish.rank {
                        LineMark(x: .value("Month", label(finish.month)), y: .value("Rank", rank), series: .value("Run", segment))
                            .foregroundStyle(palette.brand)
                            .interpolationMethod(.monotone)
                    }
                }
            }
            ForEach(finishes) { finish in
                if let rank = finish.rank {
                    PointMark(x: .value("Month", label(finish.month)), y: .value("Rank", rank))
                        .foregroundStyle(rank == 1 ? palette.brand : palette.textPrimary)
                        .annotation(position: .top) {
                            Text("#\(rank)").font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                        }
                }
            }
        }
        .chartYScale(domain: .automatic(includesZero: false, reversed: true))
        .chartYAxis {
            AxisMarks(values: Array(Set([1, lowerBound])).sorted()) { value in
                AxisGridLine()
                AxisValueLabel { Text("#\(value.as(Int.self) ?? 0)") }
            }
        }
        .chartXScale(domain: finishes.map { label($0.month) })
    }

    /// Consecutive months with a rank; a gap month starts a new series so the line breaks.
    private var segments: [[PlayerStats.MonthlyFinish]] {
        var result: [[PlayerStats.MonthlyFinish]] = [[]]
        for finish in finishes {
            if finish.rank == nil {
                if !(result.last?.isEmpty ?? true) { result.append([]) }
            } else {
                result[result.count - 1].append(finish)
            }
        }
        return result.filter { !$0.isEmpty }
    }

    private var summary: String {
        finishes.map { finish in
            let month = PlayboardMonth.label(finish.month, format: "MMMM yyyy")
            return finish.rank.map { "\(month): finished \($0) of \(finish.qualifiedPlayers)" } ?? "\(month): did not qualify"
        }.joined(separator: ". ")
    }

    private func label(_ month: String) -> String { PlayboardMonth.label(month, format: "MMM yy") }
}

/// GitHub-style attendance grid: one block of Monday-first week columns per month.
struct AttendanceHeatmap: View {
    let months: [Date]
    let activeDays: Set<Date>
    var grid = AttendanceGrid()
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
                PlayboardSectionLabel("Activity")
                HStack(alignment: .top, spacing: PlayboardSpacing.medium) {
                    weekdayAxis
                    ForEach(months, id: \.self) { month in monthBlock(month) }
                    Spacer(minLength: 0)
                }
                Text("\(playedCount) \(playedCount == 1 ? "day" : "days") played in the last \(months.count) months")
                    .font(PlayboardTypography.eyebrow())
                    .foregroundStyle(palette.textMuted)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Activity")
        .accessibilityValue("\(playedCount) days played in the last \(months.count) months")
    }

    private var playedCount: Int {
        guard let window = grid.window(for: months) else { return 0 }
        return activeDays.filter { window.contains($0) }.count
    }

    private var weekdayAxis: some View {
        VStack(alignment: .trailing, spacing: 3) {
            Text(" ").font(PlayboardTypography.eyebrow())
            ForEach(Array(["Mon", "", "Wed", "", "Fri", "", "Sun"].enumerated()), id: \.offset) { _, day in
                Text(day).font(.system(size: 9)).foregroundStyle(palette.textMuted).frame(height: 12)
            }
        }
    }

    private func monthBlock(_ month: Date) -> some View {
        let weeks = stride(from: 0, to: grid.cells(for: month).count, by: 7).map { Array(grid.cells(for: month)[$0..<$0 + 7]) }
        return VStack(alignment: .leading, spacing: 3) {
            Text(month.formatted(.dateTime.month(.abbreviated))).font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
            HStack(alignment: .top, spacing: 3) {
                ForEach(weeks.indices, id: \.self) { week in
                    VStack(spacing: 3) {
                        ForEach(0..<7, id: \.self) { weekday in
                            if let day = weeks[week][weekday] {
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(activeDays.contains(day) ? palette.brand : palette.textMuted.opacity(0.15))
                                    .frame(width: 12, height: 12)
                            } else {
                                Color.clear.frame(width: 12, height: 12)
                            }
                        }
                    }
                }
            }
        }
    }
}
