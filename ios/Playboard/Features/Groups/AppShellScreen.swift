import SwiftUI

/// Native S02 tab shell scoped by one persisted active group.
struct AppShellScreen: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var viewModel: GroupViewModel
    private let session: AuthSession
    private let signOut: () -> Void

    init(environment: AppEnvironment, session: AuthSession, signOut: @escaping () -> Void) {
        self.session = session
        self.signOut = signOut
        _viewModel = StateObject(wrappedValue: GroupViewModel(
            repository: environment.groupRepositoryFactory(session.accessToken),
            currentUserID: session.user.id
        ))
    }

    var body: some View {
        PlayboardBackground {
            Group {
                switch viewModel.state.phase {
                case .loading:
                    GroupLoadingSkeleton()
                case .failed:
                    PlayboardErrorView(message: viewModel.state.errorMessage ?? "Groups could not be loaded.") {
                        Task { await viewModel.load() }
                    }
                    .padding(PlayboardSpacing.extraLarge)
                case .loaded:
                    shell
                }
            }
        }
        .task { await viewModel.load() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, viewModel.state.phase == .loaded {
                Task { await viewModel.refresh() }
            }
        }
        .sheet(item: sheetBinding) { sheet in
            sheetView(sheet)
                .presentationDragIndicator(.visible)
        }
        .accessibilityIdentifier("signed-in-screen")
    }

    private var shell: some View {
        VStack(spacing: 0) {
            GroupSwitcherHeader(viewModel: viewModel)
                .padding(.horizontal, PlayboardSpacing.large)
                .padding(.top, PlayboardSpacing.small)

            if viewModel.state.groups.isEmpty {
                Spacer()
                PlayboardEmptyView(
                    title: "Start your first group",
                    message: "Create a badminton group or join one with an invite code."
                )
                .padding(.horizontal, PlayboardSpacing.extraLarge)
                PlayboardPrimaryButton("Create or join a group", systemImage: "person.3.fill") {
                    viewModel.presentEntry()
                }
                .padding(PlayboardSpacing.extraLarge)
                .accessibilityIdentifier("first-group-action")
                Spacer()
            } else {
                TabView {
                    ShellPlaceholder(title: "Board", message: "Your group leaderboard arrives in the next slice.", symbol: "trophy")
                        .tabItem { Label("Board", systemImage: "trophy") }
                    ShellPlaceholder(title: "Matches", message: "Match history is reserved for S04.", symbol: "list.bullet.rectangle")
                        .tabItem { Label("Matches", systemImage: "list.bullet.rectangle") }
                    ShellPlaceholder(title: "Add match", message: "Match recording is reserved for S04.", symbol: "plus.circle.fill")
                        .tabItem { Label("Add", systemImage: "plus.circle.fill") }
                    ShellPlaceholder(title: "Stats", message: "Player insights are reserved for S05.", symbol: "chart.xyaxis.line")
                        .tabItem { Label("Stats", systemImage: "chart.xyaxis.line") }
                    accountTab
                        .tabItem { Label("Profile", systemImage: "person.crop.circle") }
                }
                .accessibilityIdentifier("app-tab-shell")
            }
        }
    }

    private var accountTab: some View {
        ScrollView {
            VStack(spacing: PlayboardSpacing.large) {
                ShellPlaceholder(title: session.user.displayName, message: session.user.email, symbol: "person.crop.circle.fill")
                Button(role: .destructive, action: signOut) {
                    Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("sign-out-button")
            }
            .padding(PlayboardSpacing.extraLarge)
        }
    }

    @ViewBuilder
    private func sheetView(_ sheet: GroupSheet) -> some View {
        switch sheet {
        case .createOrJoin: GroupEntrySheet(viewModel: viewModel)
        case .rename: RenameGroupSheet(viewModel: viewModel)
        case .invite: InviteGroupSheet(viewModel: viewModel)
        case .members: MembersSheet(viewModel: viewModel)
        case .session: SessionTimeSheet(viewModel: viewModel)
        }
    }

    private var sheetBinding: Binding<GroupSheet?> {
        Binding(get: { viewModel.state.presentedSheet }, set: { if $0 == nil { viewModel.dismissSheet() } })
    }

}

private struct GroupSwitcherHeader: View {
    @ObservedObject var viewModel: GroupViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        HStack(spacing: PlayboardSpacing.medium) {
            AppWordmark(logoHeight: 28, fontSize: 22)
            Spacer(minLength: PlayboardSpacing.small)
            Menu {
                ForEach(viewModel.state.groups) { group in
                    Button {
                        Task { await viewModel.selectGroup(group.id) }
                    } label: {
                        Label(group.name, systemImage: group.id == viewModel.state.selectedGroup?.id ? "checkmark.circle.fill" : "circle")
                    }
                }
                Divider()
                Button("Create or join", systemImage: "plus") { viewModel.presentEntry() }
                Button("Members", systemImage: "person.3") { Task { await viewModel.presentMembers() } }
                if viewModel.state.selectedGroup?.canManage == true {
                    Button("Rename group", systemImage: "pencil") { viewModel.presentRename() }
                    Button("Invite players", systemImage: "square.and.arrow.up") { Task { await viewModel.presentInvite() } }
                    Button("Session time", systemImage: "clock") { viewModel.presentSession() }
                } else if viewModel.state.selectedGroup != nil {
                    Text("Admin role required for group changes")
                }
            } label: {
                HStack(spacing: PlayboardSpacing.small) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(activeColor)
                        .frame(width: 5, height: 30)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(viewModel.state.selectedGroup?.name ?? "Groups")
                            .font(PlayboardTypography.label())
                            .foregroundStyle(palette.textPrimary)
                            .lineLimit(1)
                        if let group = viewModel.state.selectedGroup {
                            Text("\(group.memberCount) players · \(group.myRole.rawValue)")
                                .font(PlayboardTypography.eyebrow())
                                .foregroundStyle(palette.textMuted)
                        }
                    }
                    Image(systemName: "chevron.down")
                        .font(.caption.bold())
                        .foregroundStyle(palette.textMuted)
                }
                .padding(.horizontal, PlayboardSpacing.medium)
                .frame(minHeight: 48)
                .background(palette.surface.opacity(0.94), in: Capsule())
                .overlay(Capsule().stroke(palette.textMuted.opacity(0.18)))
            }
            .accessibilityLabel("Active group, \(viewModel.state.selectedGroup?.name ?? "none")")
            .accessibilityHint("Opens group switching and management actions")
            .accessibilityIdentifier("group-switcher")
        }
    }

    private var activeColor: Color {
        guard let hex = viewModel.state.selectedGroup?.avatarColor else { return palette.brand }
        return Color(playboardHex: hex) ?? palette.brand
    }
}

private struct GroupLoadingSkeleton: View {
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        VStack(spacing: PlayboardSpacing.large) {
            HStack { AppWordmark(); Spacer() }
            ForEach(0..<3, id: \.self) { _ in
                RoundedRectangle(cornerRadius: 18)
                    .fill(palette.surface)
                    .frame(height: 88)
                    .overlay(alignment: .leading) {
                        VStack(alignment: .leading) {
                            Text("Loading group title").font(PlayboardTypography.title())
                            Text("Players and matches").font(PlayboardTypography.body())
                        }
                        .padding()
                        .redacted(reason: .placeholder)
                    }
            }
            Spacer()
        }
        .padding(PlayboardSpacing.extraLarge)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading groups")
    }
}

private struct ShellPlaceholder: View {
    @Environment(\.playboardPalette) private var palette
    let title: String
    let message: String
    let symbol: String

    var body: some View {
        VStack(spacing: PlayboardSpacing.large) {
            Image(systemName: symbol).font(.system(size: 38, weight: .bold)).foregroundStyle(palette.brand)
            Text(title).font(PlayboardTypography.display()).foregroundStyle(palette.textPrimary)
            Text(message).font(PlayboardTypography.body()).foregroundStyle(palette.textMuted).multilineTextAlignment(.center)
        }
        .padding(PlayboardSpacing.extraLarge)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private extension Color {
    init?(playboardHex value: String) {
        let value = value.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        guard value.count == 6, let hex = UInt32(value, radix: 16) else { return nil }
        self.init(hex: hex)
    }
}
