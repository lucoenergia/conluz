package org.lucoenergia.conluz.domain.production.plant.sharingagreement;

import java.util.UUID;

/**
 * Recomputes and persists {@code sharing_agreement.status} for one agreement, per the D4 invariant:
 * an agreement is SUPERSEDED when it has at least one coefficient and every coefficient has
 * {@code valid_to IS NOT NULL}; otherwise (and always for DRAFT, which this never touches) it is
 * PUBLISHED. This is the single place the rule is evaluated -- callers never compute or store the
 * status themselves, they only decide *when* a recompute is needed (after any write that can change
 * the answer: the cascade close, an explicit close, its reopening, or reversion to pending).
 */
public interface RecomputeSharingAgreementStatusRepository {

    void recomputeStatus(UUID sharingAgreementId);
}
