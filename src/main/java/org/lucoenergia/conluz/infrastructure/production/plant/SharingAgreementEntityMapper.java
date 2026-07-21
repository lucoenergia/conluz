package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.plant.SharingAgreement;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.springframework.stereotype.Component;

@Component
public class SharingAgreementEntityMapper extends BaseMapper<SharingAgreementEntity, SharingAgreement> {

    @Override
    public SharingAgreement map(SharingAgreementEntity entity) {
        return new SharingAgreement.Builder()
                .withId(entity.getId())
                .withPlantId(entity.getPlant().getId())
                .withName(entity.getName())
                .withNotes(entity.getNotes())
                .withStatus(entity.getStatus())
                .withInstalledPowerKw(entity.getInstalledPowerKw())
                .withCreatedAt(entity.getCreatedAt())
                .withCreatedBy(entity.getCreatedBy())
                .build();
    }
}
