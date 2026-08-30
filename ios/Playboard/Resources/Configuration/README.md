# Provider configuration

S00 deliberately starts without Google Sign-In or Firebase initialization. The app must build and launch with no provider files.

Beginning in the owning feature slice, inject non-secret identifiers through build settings based on `ProviderConfiguration.xcconfig.example`. Keep local `.xcconfig` files, `GoogleService-Info.plist`, APNs keys, signing certificates, provisioning profiles, OAuth client secrets, and service-account material out of source control and CI artifacts. Use the CI provider's encrypted environment/file facilities when those slices authorize configuration.
