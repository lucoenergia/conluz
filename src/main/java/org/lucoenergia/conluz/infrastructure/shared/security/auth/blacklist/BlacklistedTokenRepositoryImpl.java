package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedToken;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Implementation of the BlacklistedTokenRepository interface using JPA.
 */
@Repository
public class BlacklistedTokenRepositoryImpl implements BlacklistedTokenRepository {

    private final JpaBlacklistedTokenRepository jpaRepository;

    public BlacklistedTokenRepositoryImpl(JpaBlacklistedTokenRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public BlacklistedToken save(BlacklistedToken blacklistedToken) {
        BlacklistedTokenEntity entity = BlacklistedTokenEntity.fromDomain(blacklistedToken);
        BlacklistedTokenEntity savedEntity = jpaRepository.save(entity);
        return savedEntity.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByJti(String jti) {
        return jpaRepository.existsByJti(jti);
    }

    @Override
    @Transactional
    public int deleteAllExpiredBefore(Instant before) {
        return jpaRepository.deleteAllByExpirationBefore(before);
    }
}