package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection of {@link SharingAgreementFileEntity} carrying only the columns needed to render file
 * metadata, deliberately excluding {@code content} (a {@code bytea} column) so it is never loaded
 * just to answer "does this agreement have a file, and what is it called".
 */
public interface SharingAgreementFileSummaryProjection {

    UUID getId();

    UUID getSharingAgreementId();

    String getFilename();

    Instant getUploadedAt();
}
