package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.lucoenergia.conluz.domain.admin.user.auth.DeleteBlacklistedTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
@Transactional
public class DeleteBlacklistedTokenRepositoryDatabase implements DeleteBlacklistedTokenRepository {

    private final BlacklistedTokenJpaRepository jpaRepository;

    public DeleteBlacklistedTokenRepositoryDatabase(BlacklistedTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int deleteAllExpiredBefore(Instant before) {
        return jpaRepository.deleteAllByExpirationBefore(before);
    }
}
