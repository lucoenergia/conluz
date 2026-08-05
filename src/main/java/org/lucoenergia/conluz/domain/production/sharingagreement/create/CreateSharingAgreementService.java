package org.lucoenergia.conluz.domain.production.sharingagreement.create;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface CreateSharingAgreementService {

    /**
     * Creates a new DRAFT sharing agreement under the given plant. {@code installedPowerKw} is
     * supplied by the caller as a snapshot of the plant's installed power at authoring time, not
     * read from the plant record. {@code createdBy} (the acting user's id; never null on this path)
     * is carried by {@code request}.
     */
    SharingAgreement create(UUID plantId, CreateSharingAgreement request);
}
