package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedToken;
import org.lucoenergia.conluz.domain.admin.user.auth.CreateBlacklistedTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class CreateBlacklistedTokenRepositoryDatabase implements CreateBlacklistedTokenRepository {

    private final BlacklistedTokenJpaRepository jpaRepository;

    public CreateBlacklistedTokenRepositoryDatabase(BlacklistedTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public BlacklistedToken save(BlacklistedToken blacklistedToken) {
        BlacklistedTokenEntity entity = BlacklistedTokenEntity.fromDomain(blacklistedToken);
        BlacklistedTokenEntity savedEntity = jpaRepository.save(entity);
        return savedEntity.toDomain();
    }
}
