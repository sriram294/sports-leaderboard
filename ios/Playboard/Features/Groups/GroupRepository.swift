import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Stable failures surfaced by group flows.
enum GroupRepositoryError: Error, Equatable, Sendable {
    case invalidInvite
    case memberAlreadyExists
    case permissionDenied
    case invalidInput
    case offline
    case invalidResponse

    var message: String {
        switch self {
        case .invalidInvite: "That invite code is invalid, expired, or already used up."
        case .memberAlreadyExists: "That person is already in this group."
        case .permissionDenied: "Your role does not allow that group action."
        case .invalidInput: "Check the details and try again."
        case .offline: "Playboard could not reach the server. Check your connection and try again."
        case .invalidResponse: "Playboard received an unexpected response. Try again."
        }
    }
}

/// Group data boundary used by the S02 view model.
protocol GroupRepository: Sendable {
    func loadGroups() async throws -> [PlayGroup]
    func selectedGroupID() async -> String?
    func selectGroup(_ id: String?) async
    func createGroup(name: String) async throws -> PlayGroup
    func joinGroup(code: String) async throws -> PlayGroup
    func renameGroup(id: String, name: String) async throws -> PlayGroup
    func createInvite(groupID: String) async throws -> GroupInvite
    func loadRoster(groupID: String) async throws -> GroupRoster
    func addMember(groupID: String, email: String, displayName: String) async throws -> GroupMember
    func removeMember(groupID: String, userID: String) async throws
    func changeRole(groupID: String, userID: String, role: GroupRole) async throws -> GroupMember
    func updateSession(groupID: String, start: String?, end: String?) async throws -> PlayGroup
}

/// Existing-API implementation of group operations.
actor LiveGroupRepository: GroupRepository {
    private let apiClient: any APIClient
    private let selectedGroupStore: any SelectedGroupStore
    private let baseURL: URL
    private var accessToken: String
    private let refreshAccessToken: (@Sendable () async throws -> String)?
    private let decoder: JSONDecoder
    private let encoder = JSONEncoder()

    init(
        apiClient: any APIClient,
        selectedGroupStore: any SelectedGroupStore,
        baseURL: URL,
        accessToken: String,
        refreshAccessToken: (@Sendable () async throws -> String)? = nil
    ) {
        self.apiClient = apiClient
        self.selectedGroupStore = selectedGroupStore
        self.baseURL = baseURL
        self.accessToken = accessToken
        self.refreshAccessToken = refreshAccessToken
        decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
    }

    func loadGroups() async throws -> [PlayGroup] {
        try await send(path: "groups", method: "GET", response: GroupListResponse.self).groups
    }

    func selectedGroupID() async -> String? { await selectedGroupStore.selectedGroupID() }

    func selectGroup(_ id: String?) async { await selectedGroupStore.setSelectedGroupID(id) }

    func createGroup(name: String) async throws -> PlayGroup {
        try await send(path: "groups", method: "POST", body: CreateGroupBody(name: name.trimmed, sportCode: "badminton_doubles"), response: PlayGroup.self)
    }

    func joinGroup(code: String) async throws -> PlayGroup {
        try await send(path: "groups/join", method: "POST", body: JoinGroupBody(code: code.trimmed.uppercased()), response: PlayGroup.self)
    }

    func renameGroup(id: String, name: String) async throws -> PlayGroup {
        try await send(path: "groups/\(id)", method: "PATCH", body: RenameGroupBody(name: name.trimmed), response: PlayGroup.self)
    }

    func createInvite(groupID: String) async throws -> GroupInvite {
        try await send(path: "groups/\(groupID)/invites", method: "POST", body: EmptyBody(), response: GroupInvite.self)
    }

    func loadRoster(groupID: String) async throws -> GroupRoster {
        try await send(path: "groups/\(groupID)/members", method: "GET", response: GroupRoster.self)
    }

    func addMember(groupID: String, email: String, displayName: String) async throws -> GroupMember {
        try await send(path: "groups/\(groupID)/members", method: "POST", body: AddMemberBody(email: email.trimmed, displayName: displayName.trimmed), response: GroupMember.self)
    }

    func removeMember(groupID: String, userID: String) async throws {
        try await sendWithoutResponse(path: "groups/\(groupID)/members/\(userID)", method: "DELETE")
    }

    func changeRole(groupID: String, userID: String, role: GroupRole) async throws -> GroupMember {
        try await send(path: "groups/\(groupID)/members/\(userID)", method: "PATCH", body: ChangeRoleBody(role: role.rawValue), response: GroupMember.self)
    }

    func updateSession(groupID: String, start: String?, end: String?) async throws -> PlayGroup {
        try await send(path: "groups/\(groupID)/session", method: "PATCH", body: UpdateSessionBody(start: start, end: end), response: PlayGroup.self)
    }

    private func send<Response: Decodable>(path: String, method: String, response: Response.Type) async throws -> Response {
        try await perform(request(path: path, method: method), response: response)
    }

    private func send<Body: Encodable, Response: Decodable>(path: String, method: String, body: Body, response: Response.Type) async throws -> Response {
        var request = request(path: path, method: method)
        request.httpBody = try encoder.encode(body)
        return try await perform(request, response: response)
    }

    private func sendWithoutResponse(path: String, method: String) async throws {
        let apiResponse: APIResponse
        apiResponse = try await authorizedResponse(for: request(path: path, method: method))
        guard (200..<300).contains(apiResponse.statusCode) else { throw mapError(apiResponse) }
    }

    private func perform<Response: Decodable>(_ request: URLRequest, response: Response.Type) async throws -> Response {
        let apiResponse: APIResponse
        apiResponse = try await authorizedResponse(for: request)
        guard (200..<300).contains(apiResponse.statusCode) else { throw mapError(apiResponse) }
        do { return try decoder.decode(response, from: apiResponse.data) }
        catch { throw GroupRepositoryError.invalidResponse }
    }

    private func request(path: String, method: String) -> URLRequest {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = method
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return request
    }

    private func authorizedResponse(for request: URLRequest) async throws -> APIResponse {
        let first: APIResponse
        do { first = try await apiClient.response(for: request) }
        catch { throw GroupRepositoryError.offline }
        guard first.statusCode == 401, let refreshAccessToken else { return first }

        do { accessToken = try await refreshAccessToken() }
        catch { throw GroupRepositoryError.permissionDenied }
        var retry = request
        retry.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        do { return try await apiClient.response(for: retry) }
        catch { throw GroupRepositoryError.offline }
    }

    private func mapError(_ response: APIResponse) -> GroupRepositoryError {
        let code = try? decoder.decode(ProblemDetail.self, from: response.data).code
        return switch code {
        case "GROUP_INVITE_INVALID", "INVITE_EXPIRED", "INVITE_EXHAUSTED": .invalidInvite
        case "GROUP_MEMBER_EXISTS": .memberAlreadyExists
        case "GROUP_ROLE_FORBIDDEN", "GROUP_OWNER_PROTECTED", "GROUP_CANNOT_REMOVE_SELF": .permissionDenied
        case "GROUP_ROLE_INVALID", "GROUP_CANNOT_REMOVE_GUEST", "GROUP_SESSION_INVALID": .invalidInput
        default: response.statusCode == 403 ? .permissionDenied : .invalidResponse
        }
    }
}

private struct GroupListResponse: Decodable { let groups: [PlayGroup] }
private struct ProblemDetail: Decodable { let code: String? }
private struct CreateGroupBody: Encodable { let name: String; let sportCode: String }
private struct JoinGroupBody: Encodable { let code: String }
private struct RenameGroupBody: Encodable { let name: String }
private struct EmptyBody: Encodable {}
private struct AddMemberBody: Encodable { let email: String; let displayName: String }
private struct ChangeRoleBody: Encodable { let role: String }
private struct UpdateSessionBody: Encodable { let start: String?; let end: String? }

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
