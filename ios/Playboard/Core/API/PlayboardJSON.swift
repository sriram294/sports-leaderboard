import Foundation

/// Shared JSON coding rules for the Playboard API.
///
/// The backend serialises `Instant`s as ISO-8601 and includes fractional seconds whenever the
/// stored value has them (database timestamps carry microseconds), so decoding must accept both
/// `2026-10-01T18:30:00Z` and `2026-10-01T18:30:00.123456Z`. Foundation's built-in `.iso8601`
/// strategy rejects the fractional form.
enum PlayboardJSON {
    /// A decoder that accepts ISO-8601 dates with or without fractional seconds.
    static func decoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let value = try container.decode(String.self)
            guard let date = parseDate(value) else {
                throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid ISO-8601 date: \(value)")
            }
            return date
        }
        return decoder
    }

    /// An encoder that writes whole-second ISO-8601 dates, which the backend accepts.
    static func encoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }

    /// Parses an ISO-8601 instant with or without fractional seconds.
    static func parseDate(_ value: String) -> Date? {
        if let date = try? Date(value, strategy: Date.ISO8601FormatStyle(includingFractionalSeconds: true)) {
            return date
        }
        return try? Date(value, strategy: Date.ISO8601FormatStyle())
    }

    /// Formats an instant the way query-string windows (`from`/`to`) expect it.
    static func queryString(_ date: Date) -> String {
        date.ISO8601Format()
    }
}
