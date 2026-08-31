import Foundation

/// Authentication providers linked to a Playboard account.
enum AuthProvider: String, Codable, CaseIterable, Sendable {
    case google
    case apple
}

/// User identity returned with a successful Playboard sign-in.
struct AuthenticatedUser: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let displayName: String
    let email: String
    let photoURL: String?
    let avatarID: String?
    let avatarColor: String
    let authProviders: [AuthProvider]

    enum CodingKeys: String, CodingKey {
        case id
        case displayName
        case email
        case photoURL = "photoUrl"
        case avatarID = "avatarId"
        case avatarColor
        case authProviders
    }
}
