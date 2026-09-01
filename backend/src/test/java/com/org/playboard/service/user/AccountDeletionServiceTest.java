package com.org.playboard.service.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.org.playboard.common.ApiException;
import com.org.playboard.repository.user.AccountDeletionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AccountDeletionServiceTest {

    private final AccountDeletionRepository repository = mock(AccountDeletionRepository.class);
    private final AvatarStorageService avatars = mock(AvatarStorageService.class);
    private final AccountDeletionService service = new AccountDeletionService(repository, avatars);

    @Test
    void rejectsAnythingExceptTheExactConfirmation() {
        assertThatThrownBy(() -> service.deleteAccount(UUID.randomUUID(), "delete"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ApiException) error).getCode())
                        .isEqualTo("ACCOUNT_DELETE_CONFIRMATION_INVALID"));
        verifyNoInteractions(repository, avatars);
    }

    @Test
    void transfersOwnedGroupsArchivesEmptyGroupsAndPurgesIdentity() {
        UUID userId = UUID.randomUUID();
        UUID transferableGroup = UUID.randomUUID();
        UUID emptyGroup = UUID.randomUUID();
        UUID candidateMembership = UUID.randomUUID();
        when(repository.lockActiveUser(userId)).thenReturn(true);
        when(repository.lockOwnedGroupIds(userId)).thenReturn(List.of(transferableGroup, emptyGroup));
        when(repository.findOwnershipCandidate(transferableGroup, userId))
                .thenReturn(Optional.of(new AccountDeletionRepository.OwnershipCandidate(
                        candidateMembership, UUID.randomUUID())));
        when(repository.findOwnershipCandidate(emptyGroup, userId)).thenReturn(Optional.empty());

        service.deleteAccount(userId, "DELETE");

        InOrder order = inOrder(repository, avatars);
        order.verify(repository).lockActiveUser(userId);
        order.verify(repository).lockOwnedGroupIds(userId);
        order.verify(repository).findOwnershipCandidate(transferableGroup, userId);
        order.verify(repository).promoteToOwner(candidateMembership);
        order.verify(repository).findOwnershipCandidate(emptyGroup, userId);
        order.verify(repository).archiveGroup(emptyGroup);
        order.verify(avatars).remove(userId);
        order.verify(repository).removeMemberships(userId);
        order.verify(repository).deleteAccountScopedRows(userId);
        order.verify(repository).anonymizeUser(org.mockito.ArgumentMatchers.eq(userId), any());
    }
}
