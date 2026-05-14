package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Component
public class SharingAgreementEntityMapper extends BaseMapper<SharingAgreementEntity, SharingAgreement> {

    @Override
    public SharingAgreement map(SharingAgreementEntity entity) {
        return new SharingAgreement.Builder()
                .withId(entity.getId())
                .withStartDate(entity.getStartDate())
                .withEndDate(entity.getEndDate())
                .withNotes(entity.getNotes())
                .withCreatedAt(entity.getCreatedAt())
                .withUpdatedAt(entity.getUpdatedAt())
                .build();
    }
}
