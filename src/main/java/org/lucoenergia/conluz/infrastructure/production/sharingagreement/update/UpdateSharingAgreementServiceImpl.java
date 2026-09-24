package org.lucoenergia.conluz.infrastructure.production.sharingagreement.update;

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

    private final UpdateSharingAgreementRepository repository;

    public UpdateSharingAgreementServiceImpl(UpdateSharingAgreementRepository repository) {
        this.repository = repository;
    }

    @Override
    public SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update) {
        return repository.update(plantId, sharingAgreementId, update);
    }
}
