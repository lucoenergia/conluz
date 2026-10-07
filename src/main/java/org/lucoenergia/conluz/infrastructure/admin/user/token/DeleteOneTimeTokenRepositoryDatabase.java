package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.DeleteOneTimeTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Transactional
@Repository
public class DeleteOneTimeTokenRepositoryDatabase implements DeleteOneTimeTokenRepository {

    private final OneTimeTokenJpaRepository jpaRepository;

    public DeleteOneTimeTokenRepositoryDatabase(OneTimeTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int deleteFinishedBefore(Instant cutoff) {
        return jpaRepository.deleteFinishedBefore(cutoff);
    }
}
