import SwiftUI

/// Explicit, retryable confirmation surface for irreversible account deletion.
struct AccountDeletionSheet: View {
    let deleteAccount: () async throws -> Void
    let onDeleted: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.playboardPalette) private var palette
    @State private var confirmation = ""
    @State private var isDeleting = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlayboardSpacing.large) {
                    Label("This cannot be undone", systemImage: "exclamationmark.triangle")
                        .font(PlayboardTypography.title())
                        .foregroundStyle(palette.statLoss)
                    Text("Deleting your account removes your personal data, credentials, memberships, and device registrations. Shared match history is retained without linking it to you.")
                        .font(PlayboardTypography.body())
                        .foregroundStyle(palette.textPrimary)
                    Text("To continue, type DELETE.")
                        .font(PlayboardTypography.label())
                        .foregroundStyle(palette.textPrimary)
                    TextField("DELETE", text: $confirmation)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("delete-account-confirmation")
                    if let errorMessage {
                        Label(errorMessage, systemImage: "exclamationmark.triangle")
                            .font(PlayboardTypography.body())
                            .foregroundStyle(palette.statLoss)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("delete-account-error")
                    }
                    Button {
                        Task { await delete() }
                    } label: {
                        if isDeleting { ProgressView().frame(maxWidth: .infinity, minHeight: 44) }
                        else { Text("Delete my account").frame(maxWidth: .infinity, minHeight: 44) }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(palette.statLoss)
                    .disabled(isDeleting || confirmation != "DELETE")
                    .accessibilityIdentifier("delete-account-submit")
                }
                .padding(PlayboardSpacing.extraLarge)
            }
            .navigationTitle("Delete account")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(isDeleting)
                }
            }
        }
        .interactiveDismissDisabled(isDeleting)
    }

    private func delete() async {
        guard confirmation == "DELETE", !isDeleting else { return }
        isDeleting = true
        errorMessage = nil
        do {
            try await deleteAccount()
            onDeleted()
            dismiss()
        } catch let error as AccountRepositoryError {
            errorMessage = error.message
            isDeleting = false
        } catch {
            errorMessage = AccountRepositoryError.invalidResponse.message
            isDeleting = false
        }
    }
}
