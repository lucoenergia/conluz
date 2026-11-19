package org.lucoenergia.conluz.domain.production.sharingagreement.lock;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Locks a sharing agreement row for the rest of the caller's transaction, before replacing its partition
 * coefficients. It must be called inside a transaction: outside one, the lock would be released at once.
 *
 * <p>Lock ordering: after the plant row and the supply rows the coefficients reference, before the coefficient rows
 * themselves (ADR-0003 §11).</p>
 */
public interface LockSharingAgreementRepository {

    /**
     * Locks the agreement before its coefficients are replaced, so two replacements of the same agreement run one
     * after another and the second one replaces what the first one wrote (SHA-019). It waits for another replacement,
     * an update or a deletion of the agreement, and does not conflict with a write of a row that only references it,
     * such as an uploaded distributor file.
     *
     * <p>The status is read from the database under the lock, never from a copy loaded earlier in the transaction,
     * so it reflects a change committed while the caller was waiting.</p>
     *
     * @return the agreement's status once it is locked, or empty if there is no such agreement
     */
    Optional<SharingAgreementStatus> lockForCoefficients(UUID sharingAgreementId);
}
