# Provider configuration

The app builds and launches without provider files; S01 shows a deterministic
configuration-unavailable state instead of starting an SDK with empty values.

Inject `API_BASE_URL`, `GOOGLE_CLIENT_ID`, and `GOOGLE_REVERSED_CLIENT_ID`
through build settings based on `ProviderConfiguration.xcconfig.example`.
The backend separately accepts comma-separated Apple audiences through
`APPLE_CLIENT_IDS`. Keep local `.xcconfig` files, `GoogleService-Info.plist`,
APNs keys, signing certificates, provisioning profiles, OAuth client secrets,
and service-account material out of source control and CI artifacts. Use the CI
provider's encrypted environment/file facilities when those slices authorize
configuration.
