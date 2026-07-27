package org.lucoenergia.conluz.infrastructure.production.sharingagreement.update;

import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class UpdateSharingAgreementServiceImpl implements UpdateSharingAgreementService {

    private final GetSharingAgreementService getSharingAgreementService;
    private final UpdateSharingAgreementRepository repository;

    public UpdateSharingAgreementServiceImpl(GetSharingAgreementService getSharingAgreementService,
                                              UpdateSharingAgreementRepository repository) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.repository = repository;
    }

    @Override
    public SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        agreement.assertDraft();
        return repository.update(plantId, sharingAgreementId, update);
    }
}
