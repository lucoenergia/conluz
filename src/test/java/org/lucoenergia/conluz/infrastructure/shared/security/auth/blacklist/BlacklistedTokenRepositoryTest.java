package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedToken;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class BlacklistedTokenRepositoryTest {

    private final JpaBlacklistedTokenRepository jpaRepository = Mockito.mock(JpaBlacklistedTokenRepository.class);

    private final BlacklistedTokenRepositoryImpl repository = new BlacklistedTokenRepositoryImpl(jpaRepository);

    @Test
    void save_shouldConvertAndSaveToken() {
        // Given
        String jti = "test-jti";
        Instant expiration = Instant.now().plusSeconds(3600);
        BlacklistedToken token = new BlacklistedToken(jti, expiration);
        
        BlacklistedTokenEntity entity = BlacklistedTokenEntity.fromDomain(token);
        when(jpaRepository.save(any(BlacklistedTokenEntity.class))).thenReturn(entity);
        
        // When
        BlacklistedToken savedToken = repository.save(token);
        
        // Then
        verify(jpaRepository).save(any(BlacklistedTokenEntity.class));
        assertEquals(jti, savedToken.getJti());
        assertEquals(expiration, savedToken.getExpiration());
    }

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

    @Test
    void deleteAllExpiredBefore_shouldDelegateToJpaRepository() {
        // Given
        Instant now = Instant.now();
        when(jpaRepository.deleteAllByExpirationBefore(now)).thenReturn(5);
        
        // When
        int deletedCount = repository.deleteAllExpiredBefore(now);
        
        // Then
        verify(jpaRepository).deleteAllByExpirationBefore(now);
        assertEquals(5, deletedCount);
    }
}