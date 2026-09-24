package org.lucoenergia.conluz.infrastructure.shared.time;

import org.lucoenergia.conluz.domain.shared.time.ClockProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class ClockProviderImpl implements ClockProvider {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
