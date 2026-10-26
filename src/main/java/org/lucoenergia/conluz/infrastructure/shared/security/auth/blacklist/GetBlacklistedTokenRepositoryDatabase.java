package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.lucoenergia.conluz.domain.admin.user.auth.GetBlacklistedTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class GetBlacklistedTokenRepositoryDatabase implements GetBlacklistedTokenRepository {

    private final BlacklistedTokenJpaRepository jpaRepository;

    public GetBlacklistedTokenRepositoryDatabase(BlacklistedTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsByJti(String jti) {
        return jpaRepository.existsByJti(jti);
    }
}
