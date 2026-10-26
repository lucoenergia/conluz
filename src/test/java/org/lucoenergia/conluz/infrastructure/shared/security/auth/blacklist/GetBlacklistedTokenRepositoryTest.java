package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GetBlacklistedTokenRepositoryTest {

    private final BlacklistedTokenJpaRepository jpaRepository = Mockito.mock(BlacklistedTokenJpaRepository.class);

    private final GetBlacklistedTokenRepositoryDatabase repository =
            new GetBlacklistedTokenRepositoryDatabase(jpaRepository);

    @Test
    void existsByJti_shouldDelegateToJpaRepository() {
        // Given
        String jti = "test-jti";
        when(jpaRepository.existsByJti(jti)).thenReturn(true);

        // When
        boolean exists = repository.existsByJti(jti);

        // Then
        verify(jpaRepository).existsByJti(jti);
        assertTrue(exists);
    }
}
