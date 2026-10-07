package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenRetention;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class GetOneTimeTokenServiceImpl implements GetOneTimeTokenService {

    private final GetOneTimeTokenRepository repository;
    private final Clock clock;

    public GetOneTimeTokenServiceImpl(GetOneTimeTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<UserId> findOwner(String rawToken, OneTimeTokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findValidOwner(OneTimeTokenHash.of(rawToken), purpose, clock.instant());
    }

    @Override
    public long countIssuedSince(UserId userId, OneTimeTokenPurpose purpose, Instant since) {
        if (since.isBefore(clock.instant().minus(OneTimeTokenRetention.PERIOD))) {
            throw new IllegalArgumentException("Tokens are only counted within the retention period of "
                    + OneTimeTokenRetention.PERIOD);
        }
        return repository.countCreatedSince(userId, purpose, since);
    }
}
