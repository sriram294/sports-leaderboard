import SwiftUI

struct GroupEntrySheet: View {
    @ObservedObject var viewModel: GroupViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Picker("Group action", selection: Binding(get: { viewModel.state.entryMode }, set: viewModel.setEntryMode)) {
                    ForEach(GroupEntryMode.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                if viewModel.state.entryMode == .create {
                    TextField("Group name", text: Binding(get: { viewModel.state.groupName }, set: viewModel.setGroupName))
                        .textContentType(.organizationName)
                        .accessibilityIdentifier("group-name-field")
                } else {
                    TextField("Invite code", text: Binding(get: { viewModel.state.inviteCode }, set: viewModel.setInviteCode))
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("invite-code-field")
                }
                InlineGroupError(message: viewModel.state.actionMessage)
            }
            .navigationTitle(viewModel.state.entryMode == .create ? "Create a group" : "Join a group")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(viewModel.state.entryMode == .create ? "Create" : "Join") { Task { await viewModel.submitEntry() } }
                        .disabled(!viewModel.state.canSubmitEntry)
                        .accessibilityIdentifier("submit-group-entry")
                }
            }
        }
    }
}

struct RenameGroupSheet: View {
    @ObservedObject var viewModel: GroupViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                TextField("Group name", text: Binding(get: { viewModel.state.groupName }, set: viewModel.setGroupName))
                    .accessibilityIdentifier("rename-group-field")
                InlineGroupError(message: viewModel.state.actionMessage)
            }
            .navigationTitle("Rename group")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save changes") { Task { await viewModel.submitRename() } }
                        .disabled(viewModel.state.groupName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.state.isSubmitting)
                }
            }
        }
    }
}

struct InviteGroupSheet: View {
    @ObservedObject var viewModel: GroupViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: PlayboardSpacing.extraLarge) {
                if viewModel.state.isSubmitting {
                    PlayboardLoadingView(message: "Generating invite code")
                } else if let invite = viewModel.state.invite, let message = viewModel.inviteShareMessage {
                    Text(invite.code)
                        .font(.system(.largeTitle, design: .monospaced, weight: .black))
                        .textSelection(.enabled)
                        .accessibilityLabel("Invite code \(invite.code)")
                    Text("Anyone with this code can join your group.")
                        .font(PlayboardTypography.body())
                    ShareLink(item: message) { Label("Share invite", systemImage: "square.and.arrow.up") }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                        .accessibilityIdentifier("share-invite")
                } else {
                    PlayboardErrorView(message: viewModel.state.actionMessage ?? "An invite could not be created.") {
                        Task { await viewModel.presentInvite() }
                    }
                }
                Spacer()
            }
            .padding(PlayboardSpacing.extraLarge)
            .navigationTitle("Invite players")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}

struct MembersSheet: View {
    @ObservedObject var viewModel: GroupViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                if viewModel.state.isRosterLoading && viewModel.state.roster == nil {
                    PlayboardLoadingView(message: "Loading players")
                } else if let roster = viewModel.state.roster {
                    Section("Players") {
                        ForEach(roster.members) { member in memberRow(member) }
                    }
                    if !roster.guests.isEmpty {
                        Section("Guest fillers") { ForEach(roster.guests) { member in memberRow(member) } }
                    }
                    if viewModel.state.selectedGroup?.canManage == true {
                        Section("Add by email") {
                            TextField("Email", text: Binding(get: { viewModel.state.memberEmail }, set: viewModel.setMemberEmail))
                                .keyboardType(.emailAddress).textInputAutocapitalization(.never)
                            TextField("Display name", text: Binding(get: { viewModel.state.memberName }, set: viewModel.setMemberName))
                            Button("Add player") { Task { await viewModel.addMember() } }.disabled(!viewModel.state.canAddMember)
                        }
                    } else {
                        Section { Text("Only owners and admins can add or remove players.") }
                    }
                } else {
                    Button("Retry roster") { Task { await viewModel.loadRoster() } }
                }
                InlineGroupError(message: viewModel.state.actionMessage)
            }
            .refreshable { await viewModel.loadRoster() }
            .navigationTitle("Group members")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .alert("Remove player?", isPresented: removalAlertBinding, presenting: viewModel.state.pendingRemoval) { _ in
            Button("Cancel", role: .cancel) { viewModel.cancelRemoval() }
            Button("Remove", role: .destructive) { Task { await viewModel.confirmRemoval() } }
        } message: { member in
            Text("Remove \(member.displayName) from this group? Their match history remains.")
        }
    }

    private func memberRow(_ member: GroupMember) -> some View {
        HStack(spacing: PlayboardSpacing.medium) {
            PlayerAvatar(displayName: member.displayName, avatarID: member.avatarID, color: Color(playboardMemberHex: member.avatarColor), size: 40)
            VStack(alignment: .leading) {
                Text(member.displayName).font(PlayboardTypography.label())
                Text(member.role.rawValue.capitalized).font(PlayboardTypography.eyebrow())
            }
            Spacer()
            if let group = viewModel.state.selectedGroup,
               group.canChangeRole(of: member, currentUserID: viewModel.currentUserID) || group.canRemove(member, currentUserID: viewModel.currentUserID) {
                Menu {
                    if group.canChangeRole(of: member, currentUserID: viewModel.currentUserID) {
                        Button(member.role == .admin ? "Make member" : "Make admin") { Task { await viewModel.toggleRole(for: member) } }
                    }
                    if group.canRemove(member, currentUserID: viewModel.currentUserID) {
                        Button("Remove player", role: .destructive) { viewModel.requestRemoval(member) }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle").frame(width: 44, height: 44)
                }
                .accessibilityLabel("Actions for \(member.displayName)")
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var removalAlertBinding: Binding<Bool> {
        Binding(get: { viewModel.state.pendingRemoval != nil }, set: { if !$0 { viewModel.cancelRemoval() } })
    }
}

struct SessionTimeSheet: View {
    @ObservedObject var viewModel: GroupViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Start (HH:mm)", text: Binding(get: { viewModel.state.sessionStart }, set: viewModel.setSessionStart))
                        .keyboardType(.numbersAndPunctuation)
                    TextField("End (HH:mm)", text: Binding(get: { viewModel.state.sessionEnd }, set: viewModel.setSessionEnd))
                        .keyboardType(.numbersAndPunctuation)
                } footer: {
                    Text("Use 24-hour local time. Clear both fields to remove the playing window.")
                }
                if !viewModel.state.canSaveSession && !viewModel.state.isSubmitting {
                    InlineGroupError(message: "Enter both times as HH:mm, with the start before the end.")
                }
                InlineGroupError(message: viewModel.state.actionMessage)
            }
            .navigationTitle("Session time")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save changes") { Task { await viewModel.submitSession() } }.disabled(!viewModel.state.canSaveSession)
                }
            }
        }
    }
}

private struct InlineGroupError: View {
    let message: String?
    var body: some View {
        if let message {
            Label(message, systemImage: "exclamationmark.triangle.fill")
                .foregroundStyle(.red)
                .font(PlayboardTypography.body())
                .accessibilityIdentifier("group-action-error")
        }
    }
}

private extension Color {
    init(playboardMemberHex value: String) {
        let value = value.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        self = UInt32(value, radix: 16).map(Color.init(hex:)) ?? .gray
    }
}
