package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Transactional
@Service
public class RevokeOneTimeTokenServiceImpl implements RevokeOneTimeTokenService {

    private final RevokeOneTimeTokenRepository repository;
    private final Clock clock;

    public RevokeOneTimeTokenServiceImpl(RevokeOneTimeTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public boolean revokeForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose) {
        purpose.requireBoundToMembership();
        return repository.revokeActiveForMembership(userId, communityId, purpose, clock.instant());
    }
}
