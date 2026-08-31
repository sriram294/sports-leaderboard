import Foundation

/// Calendar operations injected into month-scoped leaderboard state.
protocol LeaderboardCalendaring: Sendable {
    func monthInterval(containing date: Date) -> DateInterval?
    func monthName(containing date: Date) -> String
}

/// Device-local calendar behavior used in production.
struct SystemLeaderboardCalendar: LeaderboardCalendaring {
    private let calendar: Calendar
    private let locale: Locale

    init(calendar: Calendar = .current, locale: Locale = .current) {
        self.calendar = calendar
        self.locale = locale
    }

    func monthInterval(containing date: Date) -> DateInterval? {
        calendar.dateInterval(of: .month, for: date)
    }

    func monthName(containing date: Date) -> String {
        date.formatted(.dateTime.month(.wide).locale(locale))
    }
}
