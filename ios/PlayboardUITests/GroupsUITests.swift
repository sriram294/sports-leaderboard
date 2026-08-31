import XCTest

final class GroupsUITests: XCTestCase {
    func testEmptyStateCreatesFirstGroup() {
        let app = launch(groupScenario: "empty")
        XCTAssertTrue(app.buttons["first-group-action"].waitForExistence(timeout: 3))

        app.buttons["first-group-action"].tap()
        app.textFields["group-name-field"].tap()
        app.textFields["group-name-field"].typeText("Court Crew")
        app.buttons["submit-group-entry"].tap()

        XCTAssertTrue(app.descendants(matching: .any)["app-tab-shell"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["group-switcher"].label.contains("Court Crew"))
    }

    func testEmptyStateJoinsWithInviteCode() {
        let app = launch(groupScenario: "empty")
        app.buttons["first-group-action"].tap()
        app.buttons["Join"].tap()
        app.textFields["invite-code-field"].tap()
        app.textFields["invite-code-field"].typeText("SMASH42")
        app.buttons["submit-group-entry"].tap()

        XCTAssertTrue(app.buttons["group-switcher"].label.contains("Joined Club"))
    }

    func testSwitchesAndRenamesGroups() {
        let app = launch(groupScenario: "twoGroups")
        let switcher = app.buttons["group-switcher"]
        XCTAssertTrue(switcher.waitForExistence(timeout: 3))

        switcher.tap()
        app.buttons["Sunday Shuttles"].tap()
        XCTAssertTrue(switcher.label.contains("Sunday Shuttles"))

        switcher.tap()
        app.buttons["Rename group"].tap()
        let field = app.textFields["rename-group-field"]
        field.tap()
        field.clearAndType("Sunday Racquets")
        app.buttons["Save changes"].tap()
        XCTAssertTrue(switcher.label.contains("Sunday Racquets"))
    }

    func testFailureOffersRetry() {
        let app = launch(groupScenario: "failure")
        XCTAssertTrue(app.staticTexts["Couldn't load Playboard"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["Try again"].exists)
    }

    private func launch(groupScenario: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-auth-scenario", "restore", "-ui-group-scenario", groupScenario]
        app.launch()
        return app
    }
}

private extension XCUIElement {
    func clearAndType(_ text: String) {
        guard let current = value as? String else { typeText(text); return }
        typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        typeText(text)
    }
}
