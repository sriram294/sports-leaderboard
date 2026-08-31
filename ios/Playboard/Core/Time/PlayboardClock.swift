import Foundation

/// Injectable time source for session-expiry decisions.
protocol PlayboardClock: Sendable {
    var now: Date { get }
}

/// Wall-clock time used by the production session repository.
struct SystemPlayboardClock: PlayboardClock {
    var now: Date { Date() }
}
