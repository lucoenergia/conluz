package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Transactional
@Repository
public class ConsumeOneTimeTokenRepositoryDatabase implements ConsumeOneTimeTokenRepository {

    private final JpaOneTimeTokenRepository jpaRepository;

    public ConsumeOneTimeTokenRepositoryDatabase(JpaOneTimeTokenRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UserId> consume(String tokenHash, OneTimeTokenPurpose purpose, Instant now) {
        // A single conditional update: a concurrent consumption of the same token waits on the row lock, then
        // re-evaluates the condition against the committed row and updates nothing
        if (jpaRepository.markUsed(tokenHash, purpose, now) != 1) {
            return Optional.empty();
        }
        return jpaRepository.findUserIdByTokenHash(tokenHash).map(UserId::of);
    }
}
