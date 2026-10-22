import XCTest

@MainActor
final class LeaderboardUITests: XCTestCase {
    func testPodiumAndRangeMenu() {
        let app = launch(scenario: "standard")
        XCTAssertTrue(app.descendants(matching: .any)["leaderboard-screen"].waitForExistence(timeout: 3))
        XCTAssertTrue(element(labelled: "Priya", in: app).exists)

        // The Board offers the live month plus captured monthly snapshots; All Time lives in Stats.
        app.buttons["leaderboard-range"].tap()
        // The menu item shares its title with the range button itself, so take the first match.
        let thisMonth = app.buttons.matching(NSPredicate(format: "label == %@", "This Month")).firstMatch
        XCTAssertTrue(thisMonth.waitForExistence(timeout: 3))
        thisMonth.tap()
        XCTAssertTrue(app.buttons["leaderboard-range"].label.contains("This Month"))
        attachScreenshot(app, name: "leaderboard-this-month")
    }

    func testDenseRankingsRemainScrollable() {
        let app = launch(
            scenario: "dense",
            extraArguments: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"]
        )
        XCTAssertTrue(element(labelled: "Player 2", in: app).waitForExistence(timeout: 3))
        app.swipeUp()
        XCTAssertTrue(element(labelled: "Player 12", in: app).waitForExistence(timeout: 3))
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

    /// Rows and podium slots are single accessibility elements labelled "Rank N, Name".
    private func element(labelled name: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label ENDSWITH %@", ", \(name)"))
            .firstMatch
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
