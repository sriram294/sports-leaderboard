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

extension AuthenticatedUser {
    /// Tolerates providers this build does not know (and a missing list) instead of failing sign-in.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            id: try c.decode(String.self, forKey: .id),
            displayName: try c.decode(String.self, forKey: .displayName),
            email: try c.decode(String.self, forKey: .email),
            photoURL: try c.decodeIfPresent(String.self, forKey: .photoURL),
            avatarID: try c.decodeIfPresent(String.self, forKey: .avatarID),
            avatarColor: try c.decode(String.self, forKey: .avatarColor),
            authProviders: (try c.decodeIfPresent([String].self, forKey: .authProviders) ?? []).compactMap(AuthProvider.init(rawValue:))
        )
    }
}
