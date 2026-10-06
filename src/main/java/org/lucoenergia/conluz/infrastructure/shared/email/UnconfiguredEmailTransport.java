package org.lucoenergia.conluz.infrastructure.shared.email;

import org.lucoenergia.conluz.domain.shared.email.Email;

/**
 * Used when sending is switched on but the settings are incomplete: every email fails, and is logged as any
 * other failure.
 */
class UnconfiguredEmailTransport implements EmailTransport {

    @Override
    public void send(Email email) throws EmailNotConfiguredException {
        throw new EmailNotConfiguredException();
    }
}
