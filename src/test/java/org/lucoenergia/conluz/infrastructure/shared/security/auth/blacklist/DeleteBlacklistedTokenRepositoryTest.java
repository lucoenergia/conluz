package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DeleteBlacklistedTokenRepositoryTest {

    private final BlacklistedTokenJpaRepository jpaRepository = Mockito.mock(BlacklistedTokenJpaRepository.class);

    private final DeleteBlacklistedTokenRepositoryDatabase repository =
            new DeleteBlacklistedTokenRepositoryDatabase(jpaRepository);

    @Test
    void deleteAllExpiredBefore_shouldDelegateToJpaRepository() {
        // Given
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        when(jpaRepository.deleteAllByExpirationBefore(now)).thenReturn(5);

        // When
        int deletedCount = repository.deleteAllExpiredBefore(now);

        // Then
        verify(jpaRepository).deleteAllByExpirationBefore(now);
        assertEquals(5, deletedCount);
    }
}
