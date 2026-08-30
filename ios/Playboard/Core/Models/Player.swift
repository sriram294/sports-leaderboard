import Foundation

/// Minimal shared player identity used by reusable avatar controls.
struct Player: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let displayName: String
    let avatarID: String?
    let avatarColorHex: String
}
