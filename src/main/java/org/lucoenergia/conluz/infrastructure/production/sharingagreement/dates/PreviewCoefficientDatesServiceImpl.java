package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesOutcome;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesRequest;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.PreviewCoefficientDatesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class PreviewCoefficientDatesServiceImpl implements PreviewCoefficientDatesService {

    private final CoefficientDatesPlanner planner;

    public PreviewCoefficientDatesServiceImpl(CoefficientDatesPlanner planner) {
        this.planner = planner;
    }

    /**
     * Takes no lock: a write that runs meanwhile may change the timeline, and the write re-validates everything.
     */
    @Override
    public CoefficientDatesOutcome previewDates(UUID plantId, UUID sharingAgreementId, CoefficientDatesRequest request) {
        CoefficientDatesPlanner.Prepared prepared = planner.plan(plantId, sharingAgreementId, request);
        return planner.outcome(prepared.planned().effects(), prepared.agreements());
    }
}
