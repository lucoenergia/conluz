package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Transactional(readOnly = true)
@Repository
public class GetOneTimeTokenRepositoryDatabase implements GetOneTimeTokenRepository {

    private final OneTimeTokenJpaRepository jpaRepository;

    public GetOneTimeTokenRepositoryDatabase(OneTimeTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UserId> findValidOwner(String tokenHash, OneTimeTokenPurpose purpose, Instant now) {
        return jpaRepository.findValidUserId(tokenHash, purpose, now).map(UserId::of);
    }

    @Override
    public long countCreatedSince(UserId userId, OneTimeTokenPurpose purpose, Instant since) {
        return jpaRepository.countCreatedSince(userId.getId(), purpose, since);
    }
}
