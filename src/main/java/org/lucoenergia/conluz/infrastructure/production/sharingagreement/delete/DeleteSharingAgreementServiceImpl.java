package org.lucoenergia.conluz.infrastructure.production.sharingagreement.delete;

import org.lucoenergia.conluz.domain.production.sharingagreement.delete.DeleteSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.delete.DeleteSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class DeleteSharingAgreementServiceImpl implements DeleteSharingAgreementService {

    private final GetSharingAgreementService getSharingAgreementService;
    private final DeleteSharingAgreementRepository repository;

    public DeleteSharingAgreementServiceImpl(GetSharingAgreementService getSharingAgreementService,
                                              DeleteSharingAgreementRepository repository) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.repository = repository;
    }

    @Override
    public void delete(UUID plantId, UUID sharingAgreementId) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        agreement.assertDraft();
        repository.delete(plantId, sharingAgreementId);
    }
}
