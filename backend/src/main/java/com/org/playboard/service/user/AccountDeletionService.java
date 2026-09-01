package com.org.playboard.service.user;

import com.org.playboard.common.ApiException;
import com.org.playboard.repository.user.AccountDeletionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permanently unlinks an account while retaining anonymous shared game history. */
@Service
public class AccountDeletionService {

    private static final String REQUIRED_CONFIRMATION = "DELETE";

    private final AccountDeletionRepository repository;
    private final AvatarStorageService avatarStorageService;

    public AccountDeletionService(
            AccountDeletionRepository repository, AvatarStorageService avatarStorageService) {
        this.repository = repository;
        this.avatarStorageService = avatarStorageService;
    }

    @Transactional
    public void deleteAccount(UUID userId, String confirmation) {
        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "ACCOUNT_DELETE_CONFIRMATION_INVALID",
                    "Type DELETE to confirm account deletion");
        }
        if (!repository.lockActiveUser(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }

        for (UUID groupId : repository.lockOwnedGroupIds(userId)) {
            repository.findOwnershipCandidate(groupId, userId)
                    .ifPresentOrElse(
                            candidate -> repository.promoteToOwner(candidate.membershipId()),
                            () -> repository.archiveGroup(groupId));
        }

        // Remove public personal bytes before the row stops pointing at them. A storage
        // failure keeps the database transaction active so the user can safely retry.
        avatarStorageService.remove(userId);
        repository.removeMemberships(userId);
        repository.deleteAccountScopedRows(userId);
        repository.anonymizeUser(userId, Instant.now());
    }
}
