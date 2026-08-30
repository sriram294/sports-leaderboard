import XCTest

final class DesignGalleryUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testDesignGalleryLightAndDark() throws {
        try captureGallery(appearance: "light")
        try captureGallery(appearance: "dark")
    }

    @MainActor
    private func captureGallery(appearance: String) throws {
        let app = XCUIApplication()
        app.launchArguments = ["-design-gallery", "-AppleInterfaceStyle", appearance.capitalized]
        app.launch()

        let gallery = app.scrollViews["design-gallery"]
        XCTAssertTrue(gallery.waitForExistence(timeout: 10))

        attachScreenshot(app, name: "gallery-\(appearance)-top")
        gallery.swipeUp()
        attachScreenshot(app, name: "gallery-\(appearance)-assets")
        gallery.swipeUp()
        gallery.swipeUp()
        XCTAssertTrue(app.staticTexts["No matches yet"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Try again"].exists)
        attachScreenshot(app, name: "gallery-\(appearance)-states")
    }

    @MainActor
    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
