# Feature structure

Each delivery slice adds `Features/<Feature>/` declarations together: a SwiftUI `Screen`, an `@MainActor` ViewModel, immutable `State` and actions, and the feature's Repository protocol. Concrete data implementations belong under `Core/` when shared or beside the feature when feature-private. Screens receive view models or state through initializers; previews and tests inject deterministic repositories.

S00 contains only the configuration-free `FoundationScreen`; production feature ViewModels begin with S01.
