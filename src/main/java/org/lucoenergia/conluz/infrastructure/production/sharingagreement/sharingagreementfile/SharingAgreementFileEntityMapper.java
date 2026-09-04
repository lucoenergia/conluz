package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFileSummary;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.springframework.stereotype.Component;

@Component
public class SharingAgreementFileEntityMapper extends BaseMapper<SharingAgreementFileEntity, SharingAgreementFile> {

    @Override
    public SharingAgreementFile map(SharingAgreementFileEntity entity) {
        return new SharingAgreementFile.Builder()
                .withId(entity.getId())
                .withSharingAgreementId(entity.getSharingAgreement().getId())
                .withFilename(entity.getFilename())
                .withContent(entity.getContent())
                .withContentHash(entity.getContentHash())
                .withUploadedAt(entity.getUploadedAt())
                .withUploadedBy(entity.getUploadedBy())
                .build();
    }

    public SharingAgreementFileSummary mapSummary(SharingAgreementFileSummaryProjection projection) {
        return new SharingAgreementFileSummary.Builder()
                .withId(projection.getId())
                .withFilename(projection.getFilename())
                .withUploadedAt(projection.getUploadedAt())
                .build();
    }
}
