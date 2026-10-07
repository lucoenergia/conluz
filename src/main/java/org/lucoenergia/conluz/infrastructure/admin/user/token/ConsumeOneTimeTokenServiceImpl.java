package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Transactional
@Service
public class ConsumeOneTimeTokenServiceImpl implements ConsumeOneTimeTokenService {

    private final ConsumeOneTimeTokenRepository repository;
    private final Clock clock;

    public ConsumeOneTimeTokenServiceImpl(ConsumeOneTimeTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<UserId> consume(String rawToken, OneTimeTokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.consume(OneTimeTokenHash.of(rawToken), purpose, clock.instant());
    }
}
