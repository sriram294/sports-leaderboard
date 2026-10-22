import Foundation

/// Pure calendar math behind the Profile attendance heatmap.
struct AttendanceGrid: Sendable {
    let calendar: Calendar

    init(calendar: Calendar = .current) {
        self.calendar = calendar
    }

    /// Start dates of the last `count` calendar months, oldest first, ending with the month of `today`.
    func months(endingAt today: Date, count: Int = 3) -> [Date] {
        guard let current = calendar.dateInterval(of: .month, for: today)?.start else { return [] }
        return (0..<count).reversed().compactMap { calendar.date(byAdding: .month, value: -$0, to: current) }
    }

    /// The `[first day of the earliest month, first day after the latest month)` window.
    func window(for months: [Date]) -> DateInterval? {
        guard let first = months.first, let last = months.last,
              let end = calendar.date(byAdding: .month, value: 1, to: last) else { return nil }
        return DateInterval(start: first, end: end)
    }

    /// The days of `month` laid out Monday-first, padded with nil to whole weeks.
    func cells(for month: Date) -> [Date?] {
        guard let range = calendar.range(of: .day, in: .month, for: month) else { return [] }
        let weekday = calendar.component(.weekday, from: month) // 1 = Sunday … 7 = Saturday
        let leadingBlanks = (weekday + 5) % 7
        var cells: [Date?] = Array(repeating: nil, count: leadingBlanks)
        for day in range {
            cells.append(calendar.date(byAdding: .day, value: day - 1, to: month))
        }
        while cells.count % 7 != 0 { cells.append(nil) }
        return cells
    }

    /// Buckets match instants into local calendar days.
    func days(from instants: [Date]) -> Set<Date> {
        Set(instants.map { calendar.startOfDay(for: $0) })
    }
}
