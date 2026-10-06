package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenRetention;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Transactional
@Service
public class OneTimeTokenServiceImpl implements OneTimeTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final CreateOneTimeTokenRepository createRepository;
    private final ConsumeOneTimeTokenRepository consumeRepository;
    private final GetOneTimeTokenRepository getRepository;
    private final Clock clock;

    public OneTimeTokenServiceImpl(CreateOneTimeTokenRepository createRepository,
                                   ConsumeOneTimeTokenRepository consumeRepository,
                                   GetOneTimeTokenRepository getRepository,
                                   Clock clock) {
        this.createRepository = createRepository;
        this.consumeRepository = consumeRepository;
        this.getRepository = getRepository;
        this.clock = clock;
    }

    @Override
    public RawOneTimeToken issue(UserId userId, OneTimeTokenPurpose purpose) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        RawOneTimeToken token = new RawOneTimeToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        Instant now = clock.instant();
        createRepository.replaceActive(userId, purpose, hash(token.value()), now, now.plus(purpose.lifetime()));
        return token;
    }

    @Override
    public Optional<UserId> consume(String rawToken, OneTimeTokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return consumeRepository.consume(hash(rawToken), purpose, clock.instant());
    }

    @Override
    public Optional<UserId> findOwner(String rawToken, OneTimeTokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return getRepository.findValidOwner(hash(rawToken), purpose, clock.instant());
    }

    @Override
    public long countIssuedSince(UserId userId, OneTimeTokenPurpose purpose, Instant since) {
        if (since.isBefore(clock.instant().minus(OneTimeTokenRetention.PERIOD))) {
            throw new IllegalArgumentException("Tokens are only counted within the retention period of "
                    + OneTimeTokenRetention.PERIOD);
        }
        return getRepository.countCreatedSince(userId, purpose, since);
    }

    /**
     * The lowercase hex SHA-256 hash of the token, the only form in which it is stored and looked up.
     */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
