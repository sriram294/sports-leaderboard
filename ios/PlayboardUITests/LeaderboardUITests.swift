import XCTest

final class LeaderboardUITests: XCTestCase {
    func testPodiumAndBothRanges() {
        let app = launch(scenario: "standard")
        XCTAssertTrue(app.descendants(matching: .any)["leaderboard-screen"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["Priya"].exists)

        app.buttons["leaderboard-range"].tap()
        app.buttons["All Time"].tap()
        XCTAssertTrue(app.buttons["leaderboard-range"].label.contains("All Time"))
        attachScreenshot(app, name: "leaderboard-all-time")
    }

    func testDenseRankingsRemainScrollable() {
        let app = launch(
            scenario: "dense",
            extraArguments: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"]
        )
        XCTAssertTrue(app.staticTexts["Player 2"].waitForExistence(timeout: 3))
        app.swipeUp()
        XCTAssertTrue(app.staticTexts["Player 12"].waitForExistence(timeout: 3))
        attachScreenshot(app, name: "leaderboard-dense-accessibility")
    }

    func testEmptyLeaderboardExplainsNextAction() {
        let app = launch(scenario: "empty")
        XCTAssertTrue(app.descendants(matching: .any)["leaderboard-empty"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["No matches yet"].exists)
        attachScreenshot(app, name: "leaderboard-empty")
    }

    func testFailureIsRetryable() {
        let app = launch(scenario: "failure")
        XCTAssertTrue(app.staticTexts["Couldn't load Playboard"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["Try again"].exists)
        attachScreenshot(app, name: "leaderboard-failure")
    }

    func testPodiumInLightAndDarkAppearances() {
        for style in ["Light", "Dark"] {
            let app = launch(scenario: "standard", extraArguments: ["-AppleInterfaceStyle", style])
            XCTAssertTrue(app.descendants(matching: .any)["leaderboard-screen"].waitForExistence(timeout: 3))
            attachScreenshot(app, name: "leaderboard-\(style.lowercased())")
            app.terminate()
        }
    }

    private func launch(scenario: String, extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-ui-auth-scenario", "restore",
            "-ui-group-scenario", "standard",
            "-ui-leaderboard-scenario", scenario
        ] + extraArguments
        app.launch()
        return app
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
