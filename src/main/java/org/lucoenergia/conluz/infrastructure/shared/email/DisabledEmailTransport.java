package org.lucoenergia.conluz.infrastructure.shared.email;

import org.lucoenergia.conluz.domain.shared.email.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when sending is switched off: notes that the email was not sent, and never opens a connection.
 */
class DisabledEmailTransport implements EmailTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisabledEmailTransport.class);

    @Override
    public void send(Email email) {
        LOGGER.info("Email {} not sent: sending is disabled", email.category());
    }
}
