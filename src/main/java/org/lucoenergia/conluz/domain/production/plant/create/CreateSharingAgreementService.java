package org.lucoenergia.conluz.domain.production.plant.create;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface CreateSharingAgreementService {

    /**
     * Creates a new DRAFT sharing agreement under the given plant. {@code installedPowerKw} is
     * snapshotted from the plant's current {@code totalPower} at creation time, not a live join.
     *
     * @param createdBy the acting user's id; never null on this path
     */
    SharingAgreement create(UUID plantId, String name, String notes, UUID createdBy);
}
