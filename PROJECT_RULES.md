Kotlin + Jetpack Compose only.
Material 3 design system.
MVVM + Repository pattern.
One screen per package.
Reusable UI components in a shared components package.
No business logic in composables.
Use immutable UI state.
KDoc for public classes and functions.
Follow clean architecture and SOLID principles.
Screen-level horizontal padding is 10.dp — every page's root container uses this
gutter so content lines up with the shared header across tabs. (Padding *inside* a
card, row or button is independent and unaffected.)