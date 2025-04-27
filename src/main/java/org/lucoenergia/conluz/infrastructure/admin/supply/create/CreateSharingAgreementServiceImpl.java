package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Implementation of the service for creating sharing agreements
 */
@Service
@Transactional
public class CreateSharingAgreementServiceImpl implements CreateSharingAgreementService {

    private final CreateSharingAgreementRepository repository;

    public CreateSharingAgreementServiceImpl(CreateSharingAgreementRepository repository) {
        this.repository = repository;
    }

    @Override
    public SharingAgreement create(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        return repository.create(startDate, endDate);
    }
}