import Foundation

/// Non-secret provider and API identifiers injected through build settings.
struct ProviderConfiguration: Equatable, Sendable {
    let apiBaseURL: URL?
    let googleClientID: String?

    static func from(bundle: Bundle = .main) -> ProviderConfiguration {
        ProviderConfiguration(
            apiBaseURL: value(named: "PlayboardAPIBaseURL", in: bundle).flatMap(URL.init(string:)),
            googleClientID: value(named: "GIDClientID", in: bundle)
        )
    }

    private static func value(named key: String, in bundle: Bundle) -> String? {
        guard let value = bundle.object(forInfoDictionaryKey: key) as? String else {
            return nil
        }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !trimmed.hasPrefix("$(") else {
            return nil
        }
        return trimmed
    }
}
