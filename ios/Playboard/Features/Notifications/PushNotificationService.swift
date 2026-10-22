import FirebaseCore
import FirebaseMessaging
import Foundation
import UIKit
import UserNotifications

/// Firebase Cloud Messaging bridge for group push notifications.
///
/// It stays inert unless the build bundles `GoogleService-Info.plist` (kept out of source control),
/// so unconfigured and CI builds run without Firebase instead of crashing in `FirebaseApp.configure()`.
@MainActor
final class PushNotificationService: NSObject {
    static let shared = PushNotificationService()

    private(set) var isConfigured = false
    /// The latest FCM registration token, once Firebase has issued one.
    private(set) var currentToken: String?
    /// Called with every new token so the signed-in session can register it with the backend.
    var onTokenChange: ((String) -> Void)? {
        didSet { if let currentToken { onTokenChange?(currentToken) } }
    }

    /// Configures Firebase when the provider plist is present; safe to call repeatedly.
    func configureIfAvailable() {
        guard !isConfigured, Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return }
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        isConfigured = true
    }

    /// Asks for notification permission and registers with APNs; the FCM token arrives via ``onTokenChange``.
    func requestAuthorization() async {
        guard isConfigured else { return }
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        guard granted else { return }
        UIApplication.shared.registerForRemoteNotifications()
    }

    /// Hands the APNs device token to Firebase so it can mint an FCM token.
    func setAPNSToken(_ token: Data) {
        guard isConfigured else { return }
        Messaging.messaging().apnsToken = token
    }

    fileprivate func receive(token: String) {
        guard token != currentToken else { return }
        currentToken = token
        onTokenChange?(token)
    }
}

extension PushNotificationService: MessagingDelegate {
    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Task { @MainActor in PushNotificationService.shared.receive(token: fcmToken) }
    }
}

extension PushNotificationService: UNUserNotificationCenterDelegate {
    /// Shows group notifications as banners even while Playboard is in the foreground.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}

/// Application delegate that wires APNs registration into ``PushNotificationService``.
final class PlayboardAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        PushNotificationService.shared.configureIfAvailable()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        PushNotificationService.shared.setAPNSToken(deviceToken)
    }
}
