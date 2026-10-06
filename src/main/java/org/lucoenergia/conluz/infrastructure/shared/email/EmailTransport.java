package org.lucoenergia.conluz.infrastructure.shared.email;

import org.lucoenergia.conluz.domain.shared.email.Email;

/**
 * Delivers one email on the calling thread. {@link AfterCommitEmailSender} calls it from its own threads, and
 * logs whatever it throws.
 */
interface EmailTransport {

    void send(Email email) throws Exception;
}
