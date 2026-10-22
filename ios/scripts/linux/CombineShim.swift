// Linux-only stand-in for Combine used by scripts/linux-check.sh; never part of the app.
// Minimal Linux stand-in for Combine's ObservableObject/@Published so view models type-check.
public protocol ObservableObject: AnyObject {}
@propertyWrapper public struct Published<Value> {
    public var wrappedValue: Value
    public init(wrappedValue: Value) { self.wrappedValue = wrappedValue }
}
extension Published: @unchecked Sendable {}
