import Foundation

/// A signed-in user's role within a group.
enum GroupRole: String, Codable, CaseIterable, Sendable {
    case owner
    case admin
    case member
    case guest
}

/// A group returned by the existing group summary API.
struct PlayGroup: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var name: String
    let avatarColor: String
    let sportCode: String
    var memberCount: Int
    var matchCount: Int
    let myRole: GroupRole
    var sessionStart: String?
    var sessionEnd: String?

    var canManage: Bool { myRole == .owner || myRole == .admin }
    var canChangeRoles: Bool { myRole == .owner }
}

/// One real member or reusable guest in a group roster.
struct GroupMember: Codable, Equatable, Identifiable, Sendable {
    let userID: String
    let displayName: String
    let photoURL: String?
    let avatarID: String?
    let avatarColor: String
    let role: GroupRole

    var id: String { userID }

    enum CodingKeys: String, CodingKey {
        case userID = "userId"
        case displayName
        case photoURL = "photoUrl"
        case avatarID = "avatarId"
        case avatarColor
        case role
    }
}

/// Real members and reusable guest players returned by the roster endpoint.
struct GroupRoster: Codable, Equatable, Sendable {
    let members: [GroupMember]
    let guests: [GroupMember]
}

/// A generated group invitation.
struct GroupInvite: Codable, Equatable, Sendable {
    let code: String
    let expiresAt: Date?
}
