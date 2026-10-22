import PhotosUI
import SwiftUI
import UIKit

/// A player's group-scoped profile: the own Profile tab, or a leaderboard drill-down.
struct ProfileScreen: View {
    @ObservedObject var viewModel: ProfileViewModel
    /// Shown as a gear on the own profile; nil hides it (drill-downs).
    var onOpenSettings: (() -> Void)?
    @Environment(\.playboardPalette) private var palette
    @State private var showsAvatarPicker = false

    var body: some View {
        ScrollView {
            content
                .padding(.horizontal, PlayboardSpacing.large)
                .padding(.bottom, PlayboardSpacing.section)
        }
        .refreshable { await viewModel.refresh() }
        .accessibilityIdentifier("profile-screen")
        .sheet(isPresented: renameBinding) {
            EditNameSheet(viewModel: viewModel)
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $showsAvatarPicker) {
            AvatarPickerSheet(viewModel: viewModel)
                .presentationDetents([.medium, .large])
        }
    }

    @ViewBuilder private var content: some View {
        let state = viewModel.state
        if state.groupID == nil {
            PlayboardEmptyView(title: "Choose a group", message: "Create or join a group to see your stats.")
                .padding(.top, PlayboardSpacing.section)
        } else if state.isLoading && state.stats == nil {
            PlayboardLoadingView(message: state.isOwnProfile ? "Loading your profile…" : "Loading profile…")
                .padding(.top, PlayboardSpacing.section)
        } else if let error = state.errorMessage, state.stats == nil {
            PlayboardErrorView(message: error) { viewModel.retry() }
                .padding(.top, PlayboardSpacing.section)
        } else if let stats = state.stats {
            VStack(alignment: .leading, spacing: PlayboardSpacing.large) {
                ProfileHero(
                    state: state,
                    stats: stats,
                    onEditAvatar: { showsAvatarPicker = true },
                    onEditName: viewModel.beginRename,
                    onOpenSettings: state.isOwnProfile ? onOpenSettings : nil
                )
                if let error = state.updateError {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .font(PlayboardTypography.body())
                        .foregroundStyle(palette.statLoss)
                }
                StatTilesGrid(stats: stats)
                MonthlyFinishesCard(finishes: stats.monthlyFinishes)
                if !state.attendanceMonths.isEmpty {
                    AttendanceHeatmap(months: state.attendanceMonths, activeDays: state.attendanceDays)
                }
                if !stats.trophies.isEmpty {
                    TrophyShelf(trophies: stats.trophies)
                }
                ProfilePartnersCard(viewModel: viewModel)
                if !state.recentMatches.isEmpty {
                    PlayboardSectionLabel("Recent matches")
                    ForEach(state.recentMatches) { RecentMatchCard(row: $0) }
                }
            }
            .padding(.top, PlayboardSpacing.medium)
        }
    }

    private var renameBinding: Binding<Bool> {
        Binding(get: { viewModel.state.renameSheet != nil }, set: { if !$0 { viewModel.dismissRename() } })
    }
}

private struct ProfileHero: View {
    let state: ProfileUiState
    let stats: PlayerStats
    let onEditAvatar: () -> Void
    let onEditName: () -> Void
    let onOpenSettings: (() -> Void)?
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            VStack(spacing: PlayboardSpacing.medium) {
                if let onOpenSettings {
                    HStack {
                        Spacer()
                        Button(action: onOpenSettings) {
                            Image(systemName: "gearshape").font(.title3).frame(width: 44, height: 44)
                        }
                        .accessibilityLabel("Settings")
                        .accessibilityIdentifier("profile-settings-button")
                    }
                }
                avatar
                HStack(spacing: PlayboardSpacing.small) {
                    Text(state.displayName)
                        .font(PlayboardTypography.title())
                        .foregroundStyle(palette.textPrimary)
                        .multilineTextAlignment(.center)
                    if state.isOwnProfile {
                        Button(action: onEditName) {
                            Image(systemName: "pencil").frame(width: 44, height: 44)
                        }
                        .accessibilityLabel("Edit name")
                        .accessibilityIdentifier("edit-profile-name")
                    }
                }
                Text(metaLine)
                    .font(PlayboardTypography.body())
                    .foregroundStyle(palette.textMuted)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder private var avatar: some View {
        let image = PlayerAvatar(
            displayName: state.displayName,
            avatarID: state.avatarID,
            photoURL: state.photoURL,
            color: Color(apiHex: state.avatarColor),
            size: 96,
            previewsOnTap: !state.isOwnProfile
        )
        if state.isOwnProfile {
            Button(action: onEditAvatar) {
                image
                    .overlay(alignment: .bottomTrailing) {
                        Group {
                            if state.isUpdatingAvatar {
                                ProgressView().tint(palette.onBrand)
                            } else {
                                Image(systemName: "plus").font(.caption.bold()).foregroundStyle(palette.onBrand)
                            }
                        }
                        .frame(width: 28, height: 28)
                        .background(palette.brand, in: Circle())
                    }
            }
            .buttonStyle(.plain)
            .disabled(state.isUpdatingAvatar)
            .accessibilityLabel("Change avatar")
            .accessibilityIdentifier("edit-profile-avatar")
        } else {
            image
        }
    }

    private var metaLine: String {
        var parts = ["\(stats.matchesPlayed) matches", "\(stats.winRate.formatted(.percent.precision(.fractionLength(0)))) win rate"]
        if let group = state.groupName { parts.append(group) }
        return parts.joined(separator: " · ")
    }
}

private struct StatTilesGrid: View {
    let stats: PlayerStats
    @Environment(\.playboardPalette) private var palette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: PlayboardSpacing.small), count: dynamicTypeSize.isAccessibilitySize ? 2 : 3)
        LazyVGrid(columns: columns, spacing: PlayboardSpacing.small) {
            tile("Wins", "\(stats.wins)", palette.statWin)
            tile("Losses", "\(stats.losses)", palette.statLoss)
            tile("Pts for", "\(stats.pointsFor)", palette.winRateLow)
            tile("Current streak", streakText, stats.currentStreak > 0 ? palette.statWin : palette.textPrimary)
            tile("Best streak", "\(stats.bestStreak)", palette.winRateMid)
            tile("Pts against", "\(stats.pointsAgainst)", palette.winRateLow)
        }
    }

    private var streakText: String {
        stats.currentStreak > 0 ? "\(stats.currentStreak)W" : stats.currentStreak < 0 ? "\(-stats.currentStreak)L" : "0"
    }

    private func tile(_ title: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
            Text(value).font(PlayboardTypography.title()).monospacedDigit().foregroundStyle(color)
            Text(title.uppercased()).font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted).lineLimit(2)
        }
        .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
        .padding(PlayboardSpacing.medium)
        .background(palette.surface.opacity(0.92), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(palette.textMuted.opacity(0.18)))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(value)")
    }
}

private struct TrophyShelf: View {
    let trophies: [MonthlyTrophy]
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: PlayboardSpacing.small) {
            PlayboardSectionLabel("Months won")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: PlayboardSpacing.large) {
                    ForEach(trophies) { trophy in
                        MonthlyCrownBadge(monthLabel: trophy.shortMonthLabel)
                            .accessibilityLabel("Won \(trophy.longMonthLabel)")
                    }
                }
                .padding(PlayboardSpacing.medium)
            }
            .background(palette.surface.opacity(0.92), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }
}

private struct ProfilePartnersCard: View {
    @ObservedObject var viewModel: ProfileViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            VStack(alignment: .leading, spacing: PlayboardSpacing.small) {
                Button { viewModel.togglePartners() } label: {
                    HStack {
                        Text("PARTNERS").font(PlayboardTypography.eyebrow()).tracking(1.2).foregroundStyle(palette.textMuted)
                        Spacer()
                        Image(systemName: viewModel.state.partnersExpanded ? "chevron.up" : "chevron.down")
                            .foregroundStyle(palette.textMuted)
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityValue(viewModel.state.partnersExpanded ? "Expanded" : "Collapsed")
                .accessibilityIdentifier("profile-partners-toggle")
                if viewModel.state.partnersExpanded {
                    PartnerList(
                        partners: viewModel.state.partners,
                        isLoading: viewModel.state.isLoadingPartners,
                        error: viewModel.state.partnersError
                    )
                }
            }
        }
    }
}

/// Partner rows shared by the profile and Stats partner cards.
struct PartnerList: View {
    let partners: [PartnerStats]
    let isLoading: Bool
    let error: String?
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        if isLoading {
            ProgressView().frame(maxWidth: .infinity, minHeight: 44)
        } else if let error {
            Text(error).font(PlayboardTypography.body()).foregroundStyle(palette.textMuted)
        } else if partners.isEmpty {
            Text("No partners yet.").font(PlayboardTypography.body()).foregroundStyle(palette.textMuted)
        } else {
            ForEach(partners) { partner in
                HStack(spacing: PlayboardSpacing.medium) {
                    PlayerAvatar(displayName: partner.displayName, avatarID: partner.avatarID, photoURL: partner.photoURL,
                                 color: Color(apiHex: partner.avatarColor), size: 34, previewsOnTap: false)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(partner.displayName).font(PlayboardTypography.label()).foregroundStyle(palette.textPrimary)
                        Text("\(partner.winsTogether)W–\(partner.gamesTogether - partner.winsTogether)L · \(partner.gamesTogether) games")
                            .font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                    }
                    Spacer()
                    Text(partner.winRate.formatted(.percent.precision(.fractionLength(0))))
                        .font(PlayboardTypography.label()).monospacedDigit()
                        .foregroundStyle(partner.winRate >= 0.5 ? palette.brand : palette.textPrimary)
                }
                .padding(.vertical, PlayboardSpacing.extraSmall)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(partner.displayName), \(partner.winsTogether) wins from \(partner.gamesTogether) games together")
            }
        }
    }
}

private struct RecentMatchCard: View {
    let row: RecentMatchRow
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardCard {
            HStack(alignment: .top, spacing: PlayboardSpacing.medium) {
                Text(row.isWin ? "WIN" : "LOSS")
                    .font(PlayboardTypography.eyebrow())
                    .foregroundStyle(row.isWin ? palette.onBrand : palette.textPrimary)
                    .padding(.horizontal, PlayboardSpacing.small)
                    .padding(.vertical, PlayboardSpacing.extraSmall)
                    .background(row.isWin ? palette.brand : palette.statLoss.opacity(0.25), in: Capsule())
                VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                    Text(row.playedAt.formatted(date: .abbreviated, time: .shortened))
                        .font(PlayboardTypography.eyebrow()).foregroundStyle(palette.textMuted)
                    Text(opponents).font(PlayboardTypography.body()).foregroundStyle(palette.textPrimary)
                    Text(row.scores.map { "\($0.mine)–\($0.theirs)" }.joined(separator: ", "))
                        .font(PlayboardTypography.label()).monospacedDigit().foregroundStyle(palette.textPrimary)
                }
                Spacer(minLength: 0)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var opponents: String {
        row.partnerNames.isEmpty ? "vs \(row.opponentNames)" : "w/ \(row.partnerNames) vs \(row.opponentNames)"
    }
}

private struct EditNameSheet: View {
    @ObservedObject var viewModel: ProfileViewModel
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        NavigationStack {
            Form {
                TextField("Display name", text: Binding(
                    get: { viewModel.state.renameSheet?.input ?? "" },
                    set: { viewModel.setRenameInput($0) }
                ))
                .textContentType(.name)
                .submitLabel(.done)
                .onSubmit { Task { await viewModel.submitRename() } }
                .accessibilityIdentifier("profile-name-field")
                if viewModel.state.renameSheet?.hasFailed == true {
                    Label("Couldn't update your name. Please try again.", systemImage: "exclamationmark.triangle")
                        .foregroundStyle(palette.statLoss)
                }
            }
            .navigationTitle("Edit name")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { viewModel.dismissRename() } }
                ToolbarItem(placement: .confirmationAction) {
                    if viewModel.state.renameSheet?.isSubmitting == true {
                        ProgressView()
                    } else {
                        Button("Save") { Task { await viewModel.submitRename() } }
                            .disabled(viewModel.state.renameSheet?.canSubmit != true)
                            .accessibilityIdentifier("save-profile-name")
                    }
                }
            }
        }
    }
}

private struct AvatarPickerSheet: View {
    @ObservedObject var viewModel: ProfileViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.playboardPalette) private var palette
    @State private var photoItem: PhotosPickerItem?
    @State private var photoError: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlayboardSpacing.large) {
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Label("Upload a photo", systemImage: "photo.on.rectangle")
                            .font(PlayboardTypography.label())
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("upload-avatar-photo")
                    if let photoError {
                        Text(photoError).font(PlayboardTypography.body()).foregroundStyle(palette.statLoss)
                    }
                    PlayboardSectionLabel("Or pick an avatar")
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: PlayboardSpacing.medium) {
                        ForEach(defaultAvatarIDs, id: \.self) { avatarID in
                            Button {
                                dismiss()
                                Task { await viewModel.selectAvatar(avatarID) }
                            } label: {
                                Image(avatarID)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 64, height: 64)
                                    .clipShape(Circle())
                                    .overlay(Circle().stroke(
                                        avatarID == viewModel.state.ownUser.avatarID ? palette.brand : palette.textMuted.opacity(0.2),
                                        lineWidth: avatarID == viewModel.state.ownUser.avatarID ? 3 : 1
                                    ))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Avatar \(avatarID.dropFirst("avatar".count))")
                            .accessibilityAddTraits(avatarID == viewModel.state.ownUser.avatarID ? .isSelected : [])
                        }
                    }
                }
                .padding(PlayboardSpacing.extraLarge)
            }
            .navigationTitle("Change avatar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task { await upload(item) }
        }
    }

    private func upload(_ item: PhotosPickerItem) async {
        photoError = nil
        guard let data = try? await item.loadTransferable(type: Data.self),
              let jpeg = AvatarImageProcessor.squareJPEG(from: data) else {
            photoError = "That photo couldn't be read. Try another one."
            return
        }
        dismiss()
        await viewModel.uploadPhoto(jpeg, mimeType: "image/jpeg")
    }
}

/// Center-crops a picked photo to a square avatar and re-encodes it as a compact JPEG.
enum AvatarImageProcessor {
    static func squareJPEG(from data: Data, side: CGFloat = 512) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        let source = image.size
        let crop = min(source.width, source.height)
        guard crop > 0 else { return nil }
        let scale = side / crop
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let rendered = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { _ in
            let drawSize = CGSize(width: source.width * scale, height: source.height * scale)
            image.draw(in: CGRect(
                x: (side - drawSize.width) / 2,
                y: (side - drawSize.height) / 2,
                width: drawSize.width,
                height: drawSize.height
            ))
        }
        return rendered.jpegData(compressionQuality: 0.85)
    }
}
