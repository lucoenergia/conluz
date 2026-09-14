package org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the latest uploaded file's metadata for one or many sharing agreements, without ever
 * loading the file's content.
 */
public interface GetSharingAgreementFileSummaryRepository {

    Optional<SharingAgreementFileSummary> findLatestBySharingAgreementId(UUID sharingAgreementId);

    /**
     * The latest file per agreement, for the given ids, resolved with a single query -- used to
     * enrich a list of agreements without issuing one lookup per row. Agreements with no file are
     * simply absent from the result.
     */
    List<SharingAgreementFileSummary> findLatestBySharingAgreementIds(Collection<UUID> sharingAgreementIds);
}
