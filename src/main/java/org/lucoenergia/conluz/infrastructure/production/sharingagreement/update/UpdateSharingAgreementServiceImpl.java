package org.lucoenergia.conluz.infrastructure.production.sharingagreement.update;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.SharingAgreementDistributorFileSentOnInFutureException;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreementService;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Transactional
@Service
public class UpdateSharingAgreementServiceImpl implements UpdateSharingAgreementService {

    private final UpdateSharingAgreementRepository repository;
    private final ZoneResolver zoneResolver;
    private final Clock clock;

    public UpdateSharingAgreementServiceImpl(UpdateSharingAgreementRepository repository, ZoneResolver zoneResolver,
                                             Clock clock) {
        this.repository = repository;
        this.zoneResolver = zoneResolver;
        this.clock = clock;
    }

    @Override
    public SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update) {
        LocalDate sentOn = update.getDistributorFileSentOn();
        if (sentOn != null) {
            LocalDate today = LocalDate.now(clock.withZone(zoneResolver.resolveApplicationZoneId()));
            if (sentOn.isAfter(today)) {
                throw new SharingAgreementDistributorFileSentOnInFutureException(sentOn, today);
            }
        }
        return repository.update(plantId, sharingAgreementId, update);
    }
}
