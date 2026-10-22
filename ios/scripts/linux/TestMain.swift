// Swift Testing entry point for the Linux harness.
import Testing
@main struct HarnessMain {
    static func main() async {
        await Testing.__swiftPMEntryPoint() as Never
    }
}
