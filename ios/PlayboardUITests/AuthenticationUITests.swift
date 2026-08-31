import XCTest

final class AuthenticationUITests: XCTestCase {
    func testSuccessfulSignInAndLogout() {
        let app = launch(scenario: "success")

        app.descendants(matching: .any)["google-sign-in-button"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["signed-in-screen"].waitForExistence(timeout: 3))

        app.buttons["Profile"].tap()
        app.buttons["sign-out-button"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["login-screen"].waitForExistence(timeout: 3))
    }

    func testCancellationKeepsLoginRecoverable() {
        let app = launch(scenario: "cancellation")

        app.descendants(matching: .any)["google-sign-in-button"].tap()

        let error = app.descendants(matching: .any)["login-error"]
        XCTAssertTrue(error.waitForExistence(timeout: 3))
        XCTAssertTrue(error.label.contains("cancelled"))
    }

    func testOfflineFailureKeepsLoginRecoverable() {
        let app = launch(scenario: "failure")

        app.descendants(matching: .any)["google-sign-in-button"].tap()

        let error = app.descendants(matching: .any)["login-error"]
        XCTAssertTrue(error.waitForExistence(timeout: 3))
        XCTAssertTrue(error.label.contains("offline"))
    }

    func testStoredSessionRestoresWithoutLogin() {
        let app = launch(scenario: "restore")

        XCTAssertTrue(app.descendants(matching: .any)["signed-in-screen"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.descendants(matching: .any)["login-screen"].exists)
    }

    private func launch(scenario: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-auth-scenario", scenario]
        app.launch()
        return app
    }
}
