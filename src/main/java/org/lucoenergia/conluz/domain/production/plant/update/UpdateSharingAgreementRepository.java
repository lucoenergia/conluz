package org.lucoenergia.conluz.domain.production.plant.update;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;

import java.math.BigDecimal;
import java.util.UUID;

public interface UpdateSharingAgreementRepository {

    SharingAgreement update(UUID plantId, UUID sharingAgreementId, String name, String notes, BigDecimal installedPowerKw);
}
