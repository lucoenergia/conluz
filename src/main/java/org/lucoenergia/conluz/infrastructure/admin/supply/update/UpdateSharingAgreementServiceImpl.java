package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSharingAgreementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Implementation of the service for updating sharing agreements
 */
@Service
@Transactional
public class UpdateSharingAgreementServiceImpl implements UpdateSharingAgreementService {

    private final UpdateSharingAgreementRepository repository;

    public UpdateSharingAgreementServiceImpl(UpdateSharingAgreementRepository repository) {
        this.repository = repository;
    }

    @Override
    public SharingAgreement update(UUID id, LocalDate startDate, LocalDate endDate) {
        return repository.update(id, startDate, endDate);
    }
}