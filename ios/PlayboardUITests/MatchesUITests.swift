import XCTest

final class MatchesUITests: XCTestCase {
    func testBrowsesAndExpandsMatchHistory() {
        let app = launch(matchScenario: "standard")
        app.buttons["Matches"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["matches-screen"].waitForExistence(timeout: 3))
        app.descendants(matching: .any)["match-card-match-preview"].tap()
        XCTAssertTrue(app.staticTexts["GAME BREAKDOWN"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["HISTORY"].exists)
        attachScreenshot(app, name: "matches-expanded")
    }

    func testEmptyAndFailureStates() {
        for scenario in ["empty", "failure"] {
            let app = launch(matchScenario: scenario)
            app.buttons["Matches"].tap()
            if scenario == "empty" {
                XCTAssertTrue(app.descendants(matching: .any)["matches-empty"].waitForExistence(timeout: 3))
            } else {
                XCTAssertTrue(app.buttons["Try again"].waitForExistence(timeout: 3))
            }
            attachScreenshot(app, name: "matches-\(scenario)")
            app.terminate()
        }
    }

    func testIncompleteRecordFormShowsValidation() {
        let app = launch(matchScenario: "standard")
        app.buttons["Add"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["add-match-screen"].waitForExistence(timeout: 3))
        app.buttons["team-1-slot-1"].tap()
        app.buttons["Test Player"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["add-match-validation"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["record-match-button"].isEnabled)
        attachScreenshot(app, name: "add-match-invalid")
    }

    func testSuccessfulRecordAndRetry() {
        let app = launch(matchScenario: "retry")
        fillValidForm(app)
        app.buttons["record-match-button"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["add-match-error"].waitForExistence(timeout: 3))
        app.buttons["record-match-button"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["matches-screen"].waitForExistence(timeout: 3))
    }

    func testLeavingDirtyFormRequiresConfirmation() {
        let app = launch(matchScenario: "standard")
        app.buttons["Add"].tap()
        app.buttons["team-1-slot-1"].tap()
        app.buttons["Test Player"].tap()
        app.buttons["Board"].tap()

        XCTAssertTrue(app.alerts["Discard this match?"].waitForExistence(timeout: 3))
        app.alerts["Discard this match?"].buttons["Keep editing"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["add-match-screen"].exists)
    }

    func testHistoryInLightAndDarkAppearances() {
        for style in ["Light", "Dark"] {
            let app = launch(matchScenario: "standard", extraArguments: ["-AppleInterfaceStyle", style])
            app.buttons["Matches"].tap()
            XCTAssertTrue(app.descendants(matching: .any)["matches-screen"].waitForExistence(timeout: 3))
            attachScreenshot(app, name: "matches-\(style.lowercased())")
            app.terminate()
        }
    }

    private func fillValidForm(_ app: XCUIApplication) {
        app.buttons["Add"].tap()
        for (identifier, player) in [
            ("team-1-slot-1", "Test Player"),
            ("team-1-slot-2", "Priya"),
            ("team-2-slot-1", "Dev"),
            ("team-2-slot-2", "Kiran")
        ] {
            app.buttons[identifier].tap()
            app.buttons[player].tap()
        }
        let scoreFields = app.textFields.matching(NSPredicate(format: "label CONTAINS 'score'"))
        scoreFields.element(boundBy: 0).tap()
        scoreFields.element(boundBy: 0).typeText("21")
        scoreFields.element(boundBy: 1).tap()
        scoreFields.element(boundBy: 1).typeText("12")
        XCTAssertTrue(app.buttons["record-match-button"].isEnabled)
    }

    private func launch(matchScenario: String, extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-ui-auth-scenario", "restore",
            "-ui-group-scenario", "standard",
            "-ui-match-scenario", matchScenario
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
