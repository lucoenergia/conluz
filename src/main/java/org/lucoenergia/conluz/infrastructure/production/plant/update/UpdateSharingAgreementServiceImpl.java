package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.update.UpdateSharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.plant.update.UpdateSharingAgreementService;
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
