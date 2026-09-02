import SwiftUI

/// Native doubles form for the active group's roster.
struct AddMatchScreen: View {
    @ObservedObject var viewModel: AddMatchViewModel
    let recorder: AuthenticatedUser
    @Environment(\.playboardPalette) private var palette
    @FocusState private var focusedScore: UUID?

    var body: some View {
        Group {
            if viewModel.state.groupID == nil {
                PlayboardEmptyView(title: "No active group", message: "Create or join a group before recording a match.")
                    .padding(PlayboardSpacing.extraLarge)
            } else if viewModel.state.isLoading && viewModel.state.roster.isEmpty {
                PlayboardLoadingView(message: "Loading players")
            } else if viewModel.state.roster.isEmpty, let error = viewModel.state.errorMessage {
                PlayboardErrorView(message: error, retry: viewModel.retryRoster)
                    .padding(PlayboardSpacing.extraLarge)
            } else {
                form
            }
        }
        .accessibilityIdentifier("add-match-screen")
    }

    private var form: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: PlayboardSpacing.large) {
                VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                    Text("RECORD A MATCH").font(PlayboardTypography.eyebrow()).tracking(1.2).foregroundStyle(palette.textMuted)
                    Text("Build teams and add scores").font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary)
                    Text("Recording as \(recorder.displayName) · \(recorder.email)")
                        .font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                }

                PlayboardCard {
                    VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
                        sectionLabel("BUILD TEAMS")
                        TeamBuilder(team: 1, viewModel: viewModel)
                        Divider().overlay(palette.textMuted.opacity(0.18))
                        TeamBuilder(team: 2, viewModel: viewModel)
                    }
                }

                PlayboardCard {
                    VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
                        sectionLabel("SCORE BY SET")
                        ForEach(Array(viewModel.state.sets.enumerated()), id: \.element.id) { index, set in
                            MatchSetRow(
                                number: index + 1,
                                set: set,
                                canRemove: viewModel.state.sets.count > 1,
                                focusedScore: $focusedScore,
                                onChange: { team, value in viewModel.setScore(id: set.id, team: team, value: value) },
                                onRemove: { viewModel.removeSet(id: set.id) }
                            )
                        }
                        Button { viewModel.addSet() } label: {
                            Label("Add set", systemImage: "plus.circle")
                                .frame(minHeight: 44)
                        }
                    }
                }

                PlayboardCard {
                    VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
                        sectionLabel("WHO WON?")
                        HStack(spacing: PlayboardSpacing.medium) {
                            WinnerButton(team: 1, viewModel: viewModel)
                            WinnerButton(team: 2, viewModel: viewModel)
                        }
                    }
                }

                if viewModel.state.isDirty, let validation = viewModel.state.validationMessage {
                    Label(validation, systemImage: "info.circle")
                        .font(PlayboardTypography.body())
                        .foregroundStyle(palette.textMuted)
                        .accessibilityIdentifier("add-match-validation")
                }
                if let error = viewModel.state.errorMessage {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .font(PlayboardTypography.body())
                        .foregroundStyle(palette.statLoss)
                        .accessibilityIdentifier("add-match-error")
                }

                Button {
                    focusedScore = nil
                    Task { await viewModel.submit() }
                } label: {
                    Group {
                        if viewModel.state.isSubmitting {
                            ProgressView().tint(palette.onBrand)
                        } else {
                            Label("Record match", systemImage: "checkmark.circle.fill")
                        }
                    }
                    .font(PlayboardTypography.label())
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .foregroundStyle(palette.onBrand)
                    .background(palette.brand, in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
                .disabled(!viewModel.state.canSubmit)
                .opacity(viewModel.state.canSubmit ? 1 : 0.45)
                .accessibilityIdentifier("record-match-button")
            }
            .padding(.horizontal, PlayboardSpacing.large)
            .padding(.vertical, PlayboardSpacing.medium)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func sectionLabel(_ value: String) -> some View {
        Text(value).font(PlayboardTypography.eyebrow()).tracking(1.2).foregroundStyle(palette.textMuted)
    }
}

private struct TeamBuilder: View {
    let team: Int
    @ObservedObject var viewModel: AddMatchViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: PlayboardSpacing.small) {
            Text("Team \(team)").font(PlayboardTypography.label()).foregroundStyle(palette.textPrimary)
            HStack(spacing: PlayboardSpacing.medium) {
                ForEach(0..<2, id: \.self) { slot in
                    if viewModel.state.players(for: team).indices.contains(slot) {
                        let player = viewModel.state.players(for: team)[slot]
                        Button { viewModel.removePlayer(player.userID) } label: {
                            VStack(spacing: PlayboardSpacing.extraSmall) {
                                PlayerAvatar(
                                    displayName: player.displayName,
                                    avatarID: player.avatarID,
                                    photoURL: player.photoURL,
                                    color: Color(addMatchHex: player.avatarColor),
                                    size: 48
                                )
                                Text(player.role == .guest ? "Guest" : player.displayName)
                                    .font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textPrimary).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity, minHeight: 76)
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint("Removes this player from Team \(team)")
                    } else {
                        Menu {
                            let available = viewModel.state.roster.filter { !viewModel.state.assignedPlayerIDs.contains($0.userID) }
                            if available.isEmpty {
                                Text("No players available")
                            } else {
                                ForEach(available) { player in
                                    Button(player.role == .guest ? "Guest" : player.displayName) {
                                        viewModel.addPlayer(player.userID, to: team)
                                    }
                                }
                            }
                        } label: {
                            VStack(spacing: PlayboardSpacing.extraSmall) {
                                Image(systemName: "plus").font(.title2.bold()).frame(width: 48, height: 48)
                                    .background(palette.textMuted.opacity(0.12), in: Circle())
                                Text("Choose player").font(PlayboardTypography.eyebrow())
                            }
                            .frame(maxWidth: .infinity, minHeight: 76)
                        }
                        .accessibilityIdentifier("team-\(team)-slot-\(slot + 1)")
                    }
                }
            }
        }
    }
}

private struct MatchSetRow: View {
    let number: Int
    let set: MatchSetDraft
    let canRemove: Bool
    var focusedScore: FocusState<UUID?>.Binding
    let onChange: (Int, String) -> Void
    let onRemove: () -> Void
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        HStack(spacing: PlayboardSpacing.small) {
            Text("Set \(number)").font(PlayboardTypography.label()).foregroundStyle(palette.textPrimary).frame(minWidth: 44, alignment: .leading)
            scoreField("Team 1", value: set.team1, team: 1)
            Text("–").foregroundStyle(palette.textMuted)
            scoreField("Team 2", value: set.team2, team: 2)
            if canRemove {
                Button(role: .destructive, action: onRemove) {
                    Image(systemName: "minus.circle").frame(width: 44, height: 44)
                }
                .accessibilityLabel("Remove set \(number)")
            }
        }
    }

    private func scoreField(_ label: String, value: String, team: Int) -> some View {
        TextField(label, text: Binding(get: { value }, set: { onChange(team, $0) }))
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .font(PlayboardTypography.title()).monospacedDigit()
            .frame(minWidth: 58, minHeight: 44)
            .background(palette.textMuted.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
            .focused(focusedScore, equals: set.id)
            .accessibilityLabel("Set \(number), \(label) score")
    }
}

private struct WinnerButton: View {
    let team: Int
    @ObservedObject var viewModel: AddMatchViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        let selected = viewModel.state.selectedWinner == team
        Button { viewModel.selectWinner(team) } label: {
            VStack(spacing: PlayboardSpacing.small) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                Text("Team \(team)").font(PlayboardTypography.label())
                Text(names).font(PlayboardTypography.eyebrow()).lineLimit(2).multilineTextAlignment(.center)
            }
            .foregroundStyle(selected ? palette.brand : palette.textPrimary)
            .frame(maxWidth: .infinity, minHeight: 100)
            .background(palette.textMuted.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(selected ? palette.brand : palette.textMuted.opacity(0.2), lineWidth: selected ? 2 : 1))
        }
        .buttonStyle(.plain)
        .accessibilityValue(selected ? "Selected" : "Not selected")
        .accessibilityIdentifier("winner-team-\(team)")
    }

    private var names: String {
        let players = viewModel.state.players(for: team)
        return players.isEmpty ? "? & ?" : players.map { $0.role == .guest ? "Guest" : $0.displayName }.joined(separator: " & ")
    }
}

private extension Color {
    init(addMatchHex value: String) {
        let cleaned = value.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        self = UInt32(cleaned, radix: 16).map(Color.init(hex:)) ?? .gray
    }
}
