package org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.create;

import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementService;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetAllSharingAgreementsRepository;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.OverlappingSharingAgreementException;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSharingAgreementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CreateSharingAgreementServiceImpl implements CreateSharingAgreementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateSharingAgreementServiceImpl.class);
    private final CreateSharingAgreementRepository createSharingAgreementRepository;
    private final GetAllSharingAgreementsRepository getAllSharingAgreementsRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;
    private final UpdateSharingAgreementRepository updateSharingAgreementRepository;

    public CreateSharingAgreementServiceImpl(CreateSharingAgreementRepository createSharingAgreementRepository,
                                             GetAllSharingAgreementsRepository getAllSharingAgreementsRepository,
                                             GetSharingAgreementRepository getSharingAgreementRepository,
                                             UpdateSharingAgreementRepository updateSharingAgreementRepository) {
        this.createSharingAgreementRepository = createSharingAgreementRepository;
        this.getAllSharingAgreementsRepository = getAllSharingAgreementsRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.updateSharingAgreementRepository = updateSharingAgreementRepository;
    }

    @Override
    public SharingAgreement create(CreateSharingAgreement command) {
        if (command.getEndDate() != null && command.getStartDate().isAfter(command.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before end date");
        }

        checkOverlapWithExistingAgreements(command);

        closeCurrentActiveAgreement(command.getStartDate());

        return createSharingAgreementRepository.create(command);
    }

    private void closeCurrentActiveAgreement(LocalDate newStartDate) {
        Optional<SharingAgreement> activeAgreement = getSharingAgreementRepository.findFirstByEndDateIsNull();
        if (activeAgreement.isEmpty()) {
            return;
        }

        SharingAgreement active = activeAgreement.get();

        updateSharingAgreementRepository.update(active.getId(), active.getStartDate(), newStartDate.minusDays(1));

        LOGGER.info("Closed agreement {} with start date {} and end date {}", active.getId(), active.getStartDate(), active.getEndDate());
    }

    private void checkOverlapWithExistingAgreements(CreateSharingAgreement newAgreement) {
        List<SharingAgreement> allAgreements = getAllSharingAgreementsRepository.findAll();

        LocalDate newAgreementEndDate = newAgreement.getEndDate();

        for (SharingAgreement existingAgreement : allAgreements) {

            LocalDate existingStart = existingAgreement.getStartDate();
            LocalDate existingEnd = existingAgreement.getEndDate();

            LocalDate effectiveExistingEnd = existingEnd != null ? existingEnd : LocalDate.MAX;
            LocalDate effectiveNewEnd = newAgreementEndDate != null ? newAgreementEndDate : LocalDate.MAX;

            if (existingStart.isBefore(effectiveNewEnd) || newAgreement.getStartDate().isBefore(effectiveExistingEnd)) {
                throw new OverlappingSharingAgreementException(newAgreement.getStartDate(), newAgreement.getEndDate(), existingAgreement.getId());
            }
        }
    }
}
