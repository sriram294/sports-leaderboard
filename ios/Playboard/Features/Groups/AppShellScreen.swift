import SwiftUI

/// Signed-in tab shell scoped by one persisted active group.
struct AppShellScreen: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var viewModel: GroupViewModel
    @StateObject private var boardViewModel: BoardViewModel
    @StateObject private var matchesViewModel: MatchesViewModel
    @StateObject private var addMatchViewModel: AddMatchViewModel
    @StateObject private var profileViewModel: ProfileViewModel
    @StateObject private var statsViewModel: StatsViewModel
    @State private var selectedTab = AppTab.board
    @State private var pendingTab: AppTab?
    @State private var showsSettings = false
    @State private var viewedPlayer: ViewedPlayer?
    @State private var availableUpdate: AppUpdate?
    private let session: AuthSession
    private let profileRepository: any ProfileRepository
    private let deviceRepository: any DeviceRepository
    private let updateRepository: any UpdateRepository
    private let deleteAccount: () async throws -> Void
    private let signOut: () -> Void

    init(
        environment: AppEnvironment,
        session: AuthSession,
        onUserUpdated: @escaping @MainActor (AuthenticatedUser) -> Void = { _ in },
        deleteAccount: @escaping () async throws -> Void,
        signOut: @escaping () -> Void
    ) {
        self.session = session
        self.deleteAccount = deleteAccount
        self.signOut = signOut
        let groupRepository = environment.groupRepositoryFactory(session.accessToken)
        let matchRepository = environment.matchRepositoryFactory(session.accessToken)
        let leaderboardRepository = environment.leaderboardRepositoryFactory(session.accessToken)
        let profileRepository = environment.profileRepositoryFactory(session.accessToken)
        self.profileRepository = profileRepository
        deviceRepository = environment.deviceRepositoryFactory(session.accessToken)
        updateRepository = environment.updateRepository
        _viewModel = StateObject(wrappedValue: GroupViewModel(
            repository: groupRepository,
            currentUserID: session.user.id
        ))
        _boardViewModel = StateObject(wrappedValue: BoardViewModel(repository: leaderboardRepository))
        _matchesViewModel = StateObject(wrappedValue: MatchesViewModel(repository: matchRepository, currentUserID: session.user.id))
        _addMatchViewModel = StateObject(wrappedValue: AddMatchViewModel(
            matchRepository: matchRepository,
            groupRepository: groupRepository
        ))
        _profileViewModel = StateObject(wrappedValue: ProfileViewModel(
            repository: profileRepository,
            user: session.user,
            onUserUpdated: onUserUpdated
        ))
        _statsViewModel = StateObject(wrappedValue: StatsViewModel(
            leaderboardRepository: leaderboardRepository,
            matchRepository: matchRepository,
            profileRepository: profileRepository,
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
        .task { await startServices() }
        .onChange(of: viewModel.state.selectedGroup, initial: true) { old, group in
            if old?.id != group?.id { viewedPlayer = nil }
            boardViewModel.select(group: group)
            matchesViewModel.select(group: group)
            addMatchViewModel.select(group: group)
            profileViewModel.select(group: group)
            statsViewModel.select(group: group)
        }
        .onChange(of: addMatchViewModel.state.successfulMatchID) { _, matchID in
            guard matchID != nil else { return }
            let wasEdit = addMatchViewModel.state.lastSuccessWasEdit
            Task {
                // Edits return to the Matches log; new results go to the Board.
                selectedTab = wasEdit ? .matches : .board
                addMatchViewModel.resetAfterSuccess()
                await refreshAfterDataChange()
            }
        }
        .onChange(of: matchesViewModel.state.lastDeletedMatchID) { _, matchID in
            // A deleted result changes the board, stats, and profile too.
            if matchID != nil { Task { await refreshAfterDataChange(includeMatches: false) } }
        }
        .onChange(of: session.user) { _, user in profileViewModel.updateOwnUser(user) }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, viewModel.state.phase == .loaded {
                Task { await viewModel.refresh() }
            }
        }
        .sheet(item: sheetBinding) { sheet in
            sheetView(sheet)
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showsSettings) {
            SettingsScreen(user: session.user, signOut: signOutAndUnregister, deleteAccount: deleteAccount)
        }
        .sheet(item: $viewedPlayer) { player in
            PlayerProfileSheet(
                repository: profileRepository,
                user: session.user,
                player: player,
                group: viewModel.state.selectedGroup
            )
        }
        .accessibilityIdentifier("signed-in-screen")
        .alert("Discard this match?", isPresented: discardAlertBinding) {
            Button("Keep editing", role: .cancel) { pendingTab = nil }
            Button("Discard", role: .destructive) {
                addMatchViewModel.discardChanges()
                if let pendingTab { selectedTab = pendingTab }
                pendingTab = nil
            }
        } message: {
            Text(addMatchViewModel.state.isEditing
                 ? "Your changes to this match have not been saved."
                 : "Your selected players and scores have not been recorded.")
        }
    }

    private var shell: some View {
        VStack(spacing: 0) {
            GroupSwitcherHeader(viewModel: viewModel, onOpenSettings: { showsSettings = true })
                .padding(.horizontal, PlayboardSpacing.large)
                .padding(.top, PlayboardSpacing.small)

            if let update = availableUpdate, let url = update.downloadURL {
                UpdateBanner(versionName: update.versionName, url: url) { availableUpdate = nil }
                    .padding(.horizontal, PlayboardSpacing.large)
                    .padding(.top, PlayboardSpacing.small)
            }

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
                TabView(selection: tabBinding) {
                    BoardScreen(
                        viewModel: boardViewModel,
                        groupName: viewModel.state.selectedGroup?.name ?? "",
                        onPlayerTap: openProfile
                    )
                    .tabItem { Label("Board", systemImage: "trophy") }
                    .tag(AppTab.board)
                    MatchesScreen(viewModel: matchesViewModel, onEdit: { matchID in
                        addMatchViewModel.startEditing(matchID: matchID)
                        selectedTab = .add
                    })
                    .tabItem { Label("Matches", systemImage: "list.bullet.rectangle") }
                    .tag(AppTab.matches)
                    AddMatchScreen(viewModel: addMatchViewModel, recorder: session.user)
                        .tabItem { Label("Add", systemImage: "plus.circle.fill") }
                        .tag(AppTab.add)
                    StatsScreen(viewModel: statsViewModel)
                        .tabItem { Label("Stats", systemImage: "chart.xyaxis.line") }
                        .tag(AppTab.stats)
                    ProfileScreen(viewModel: profileViewModel, onOpenSettings: { showsSettings = true })
                        .tabItem { Label("Profile", systemImage: "person.crop.circle") }
                        .tag(AppTab.profile)
                }
                .accessibilityIdentifier("app-tab-shell")
            }
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

    private var tabBinding: Binding<AppTab> {
        Binding(
            get: { selectedTab },
            set: { requested in
                if selectedTab == .add, requested != .add, addMatchViewModel.state.isDirty {
                    pendingTab = requested
                } else {
                    selectedTab = requested
                }
            }
        )
    }

    private var discardAlertBinding: Binding<Bool> {
        Binding(get: { pendingTab != nil }, set: { if !$0 { pendingTab = nil } })
    }

    /// Tapping yourself on the leaderboard opens the Profile tab instead of a read-only copy.
    private func openProfile(_ entry: LeaderboardEntry) {
        if entry.userID == session.user.id {
            selectedTab = .profile
        } else {
            viewedPlayer = ViewedPlayer(userID: entry.userID, displayName: entry.displayName)
        }
    }

    private func refreshAfterDataChange(includeMatches: Bool = true) async {
        await viewModel.refresh()
        if includeMatches { await matchesViewModel.refresh() }
        boardViewModel.retry()
        await statsViewModel.refresh()
        await profileViewModel.refresh()
    }

    /// Registers for push (when Firebase is configured) and checks for a newer App Store build.
    private func startServices() async {
        let devices = deviceRepository
        PushNotificationService.shared.onTokenChange = { token in
            Task { try? await devices.register(token: token) }
        }
        await PushNotificationService.shared.requestAuthorization()
        if let update = try? await updateRepository.latest(), update.isNewer(thanBuild: Self.currentBuild) {
            availableUpdate = update
        }
    }

    private func signOutAndUnregister() {
        PushNotificationService.shared.onTokenChange = nil
        let token = PushNotificationService.shared.currentToken
        let devices = deviceRepository
        let signOut = signOut
        Task {
            if let token { try? await devices.unregister(token: token) }
            signOut()
        }
    }

    private static var currentBuild: Int {
        Int(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "") ?? 0
    }
}

/// A leaderboard player opened from the Board.
private struct ViewedPlayer: Identifiable, Equatable {
    let userID: String
    let displayName: String
    var id: String { userID }
}

/// Another player's profile, presented over the Board with its own view model.
private struct PlayerProfileSheet: View {
    @StateObject private var viewModel: ProfileViewModel
    private let player: ViewedPlayer
    private let group: PlayGroup?
    @Environment(\.dismiss) private var dismiss

    init(repository: any ProfileRepository, user: AuthenticatedUser, player: ViewedPlayer, group: PlayGroup?) {
        self.player = player
        self.group = group
        _viewModel = StateObject(wrappedValue: ProfileViewModel(repository: repository, user: user, viewedUserID: player.userID))
    }

    var body: some View {
        NavigationStack {
            PlayboardBackground {
                ProfileScreen(viewModel: viewModel)
            }
            .navigationTitle(player.displayName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .task { viewModel.select(group: group) }
        .accessibilityIdentifier("player-profile-sheet")
    }
}

/// Prompt shown when the backend advertises a newer iOS build.
private struct UpdateBanner: View {
    let versionName: String?
    let url: URL
    let onDismiss: () -> Void
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        HStack(spacing: PlayboardSpacing.medium) {
            Image(systemName: "arrow.down.app").foregroundStyle(palette.brand).accessibilityHidden(true)
            Text(versionName.map { "Playboard \($0) is available." } ?? "A new version of Playboard is available.")
                .font(PlayboardTypography.body())
                .foregroundStyle(palette.textPrimary)
            Spacer(minLength: 0)
            Link("Update", destination: url).font(PlayboardTypography.label())
            Button(action: onDismiss) { Image(systemName: "xmark").frame(width: 44, height: 44) }
                .accessibilityLabel("Dismiss update")
        }
        .padding(.leading, PlayboardSpacing.medium)
        .background(palette.surface.opacity(0.94), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityIdentifier("update-banner")
    }
}

private enum AppTab: Hashable {
    case board
    case matches
    case add
    case stats
    case profile
}

private struct GroupSwitcherHeader: View {
    @ObservedObject var viewModel: GroupViewModel
    let onOpenSettings: () -> Void
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
            Button(action: onOpenSettings) {
                Image(systemName: "gearshape").foregroundStyle(palette.textMuted).frame(width: 44, height: 44)
            }
            .accessibilityLabel("Settings")
            .accessibilityIdentifier("header-settings-button")
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

private extension Color {
    init?(playboardHex value: String) {
        let value = value.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        guard value.count == 6, let hex = UInt32(value, radix: 16) else { return nil }
        self.init(hex: hex)
    }
}
