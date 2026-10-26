package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedToken;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CreateBlacklistedTokenRepositoryTest {

    private final BlacklistedTokenJpaRepository jpaRepository = Mockito.mock(BlacklistedTokenJpaRepository.class);

    private final CreateBlacklistedTokenRepositoryDatabase repository =
            new CreateBlacklistedTokenRepositoryDatabase(jpaRepository);

    @Test
    void save_shouldConvertAndSaveToken() {
        // Given
        String jti = "test-jti";
        Instant expiration = Instant.parse("2026-01-01T11:00:00Z");
        BlacklistedToken token = new BlacklistedToken(jti, expiration, Instant.parse("2026-01-01T10:00:00Z"));

        BlacklistedTokenEntity entity = BlacklistedTokenEntity.fromDomain(token);
        when(jpaRepository.save(any(BlacklistedTokenEntity.class))).thenReturn(entity);

        // When
        BlacklistedToken savedToken = repository.save(token);

        // Then
        verify(jpaRepository).save(any(BlacklistedTokenEntity.class));
        assertEquals(jti, savedToken.getJti());
        assertEquals(expiration, savedToken.getExpiration());
    }
}
