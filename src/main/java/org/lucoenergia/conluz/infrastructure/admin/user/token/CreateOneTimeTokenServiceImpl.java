package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Transactional
@Service
public class CreateOneTimeTokenServiceImpl implements CreateOneTimeTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final CreateOneTimeTokenRepository repository;
    private final Clock clock;

    public CreateOneTimeTokenServiceImpl(CreateOneTimeTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public RawOneTimeToken issue(UserId userId, OneTimeTokenPurpose purpose) {
        purpose.requireBoundOnlyToUser();
        RawOneTimeToken token = newToken();
        Instant now = clock.instant();
        repository.replaceActive(userId, purpose, OneTimeTokenHash.of(token.value()), now, now.plus(purpose.lifetime()));
        return token;
    }

    @Override
    public RawOneTimeToken issueForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose) {
        purpose.requireBoundToMembership();
        RawOneTimeToken token = newToken();
        Instant now = clock.instant();
        repository.replaceActiveForMembership(userId, communityId, purpose, OneTimeTokenHash.of(token.value()), now,
                now.plus(purpose.lifetime()));
        return token;
    }

    private RawOneTimeToken newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return new RawOneTimeToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
}
