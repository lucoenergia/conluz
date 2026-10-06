package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Transactional
@Repository
public class CreateOneTimeTokenRepositoryDatabase implements CreateOneTimeTokenRepository {

    private final JpaOneTimeTokenRepository jpaRepository;

    public CreateOneTimeTokenRepositoryDatabase(JpaOneTimeTokenRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void replaceActive(UserId userId, OneTimeTokenPurpose purpose, String tokenHash, Instant createdAt,
                              Instant expiresAt) {
        // The user row first, then the token rows: a concurrent issuer for the same user waits here until this
        // transaction commits, and then revokes the token inserted below
        if (jpaRepository.lockUser(userId.getId()).isEmpty()) {
            throw new UserNotFoundException(userId);
        }
        jpaRepository.revokeActive(userId.getId(), purpose, createdAt);
        jpaRepository.saveAndFlush(new OneTimeTokenEntity(UUID.randomUUID(), userId.getId(), purpose, tokenHash,
                createdAt, expiresAt));
    }
}
