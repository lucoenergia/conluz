package org.lucoenergia.conluz.domain.shared.email;

/**
 * Sends one plain-text email to one recipient.
 *
 * <p>Sending never blocks the caller and never fails it: the email is handed to a background sender, and a
 * delivery failure is logged, not thrown. There is no retry.</p>
 *
 * <p>When it is called inside a transaction, the email is handed over only once that transaction commits, and
 * not at all if it rolls back. Outside a transaction it is handed over at once: there is no outcome to wait for.
 * </p>
 */
public interface EmailSender {

    void send(Email email);
}
