package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.activation;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.RecomputeSharingAgreementStatusRepository;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationError;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementPlantMismatchException;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Transactional
@Service
public class CoefficientActivationServiceImpl implements CoefficientActivationService {

    private final GetSharingAgreementService getSharingAgreementService;
    private final GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    private final SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    private final RecomputeSharingAgreementStatusRepository recomputeStatusRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final ZoneResolver zoneResolver;

    public CoefficientActivationServiceImpl(GetSharingAgreementService getSharingAgreementService,
                                             GetSupplyPartitionCoefficientRepository getCoefficientRepository,
                                             SaveSupplyPartitionCoefficientRepository saveCoefficientRepository,
                                             RecomputeSharingAgreementStatusRepository recomputeStatusRepository,
                                             GetSupplyRepository getSupplyRepository,
                                             ZoneResolver zoneResolver) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.getCoefficientRepository = getCoefficientRepository;
        this.saveCoefficientRepository = saveCoefficientRepository;
        this.recomputeStatusRepository = recomputeStatusRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public List<SupplyPartitionCoefficient> setValidFrom(UUID plantId, UUID sharingAgreementId, LocalDate appliedOn,
                                                           List<UUID> coefficientIds) {
        loadAgreement(plantId, sharingAgreementId);
        ZoneId zoneId = zoneResolver.resolveZoneId(plantId);
        LocalDate today = LocalDate.now(zoneId);
        Instant newValidFrom = appliedOn == null ? null : appliedOn.atStartOfDay(zoneId).toInstant();

        Map<UUID, SupplyPartitionCoefficient> foundById = fetch(sharingAgreementId, coefficientIds);
        List<CoefficientActivationError> errors = new ArrayList<>();
        List<SupplyPartitionCoefficient> writes = new ArrayList<>();

        for (UUID id : distinct(coefficientIds)) {
            SupplyPartitionCoefficient coefficient = foundById.get(id);
            if (coefficient == null) {
                errors.add(error(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT, id, null));
                continue;
            }
            if (Objects.equals(coefficient.getValidFrom(), newValidFrom)) {
                continue; // true no-op: value already persisted -- nothing staged, no recompute
            }
            if (newValidFrom != null && appliedOn.isAfter(today)) {
                errors.add(error(CoefficientActivationErrorCode.DATE_IN_FUTURE, coefficient));
                continue;
            }

            // Same boundary-match lookup for activate, correct, AND revert: "the row whose validTo
            // currently equals this coefficient's own current validFrom" -- with a null current
            // validFrom (pure activation) naturally resolving to "the currently open row" instead.
            Optional<SupplyPartitionCoefficient> predecessor = getCoefficientRepository.findPredecessor(
                    plantId, coefficient.getSupplyId(), coefficient.getId(), coefficient.getValidFrom());

            if (newValidFrom == null) {
                // Revert to pending -- the splice. predecessor.validTo takes over coefficient's own
                // former validTo (which may itself be null, reopening the predecessor to infinity).
                writes.add(withValidFromTo(coefficient, null, null));
                predecessor.ifPresent(p -> writes.add(withValidFromTo(p, p.getValidFrom(), coefficient.getValidTo())));
            } else {
                // Activate (coefficient.getValidFrom() was null) or correct (already set) -- same path.
                if (predecessor.isPresent() && !newValidFrom.isAfter(predecessor.get().getValidFrom())) {
                    // Also catches the empty-range case newValidFrom == predecessor.validFrom.
                    errors.add(error(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_AFTER_PREDECESSOR, coefficient));
                    continue;
                }
                if (coefficient.getValidTo() != null && !newValidFrom.isBefore(coefficient.getValidTo())) {
                    errors.add(error(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR, coefficient));
                    continue;
                }
                writes.add(withValidFromTo(coefficient, newValidFrom, coefficient.getValidTo()));
                predecessor.ifPresent(p -> writes.add(withValidFromTo(p, p.getValidFrom(), newValidFrom)));
            }
        }

        if (!errors.isEmpty()) {
            throw new CoefficientActivationException(errors);
        }

        return applyAndRecompute(writes);
    }

    @Override
    public List<SupplyPartitionCoefficient> setValidTo(UUID plantId, UUID sharingAgreementId, LocalDate closedOn,
                                                         List<UUID> coefficientIds) {
        loadAgreement(plantId, sharingAgreementId);
        ZoneId zoneId = zoneResolver.resolveZoneId(plantId);
        LocalDate today = LocalDate.now(zoneId);
        Instant newValidTo = closedOn == null ? null : closedOn.atStartOfDay(zoneId).toInstant();

        Map<UUID, SupplyPartitionCoefficient> foundById = fetch(sharingAgreementId, coefficientIds);
        List<CoefficientActivationError> errors = new ArrayList<>();
        List<SupplyPartitionCoefficient> writes = new ArrayList<>();

        for (UUID id : distinct(coefficientIds)) {
            SupplyPartitionCoefficient coefficient = foundById.get(id);
            if (coefficient == null) {
                errors.add(error(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT, id, null));
                continue;
            }
            // Checked BEFORE the no-op comparison, deliberately: idempotency applies only to active
            // coefficients (requested value already persisted AND the coefficient is active), never to
            // a pending one -- closing/reopening a coefficient that was never activated is always an
            // error, even when closedOn == null already matches its (also null) validTo.
            if (coefficient.getValidFrom() == null) {
                errors.add(error(CoefficientActivationErrorCode.COEFFICIENT_NOT_ACTIVE, coefficient));
                continue;
            }
            if (Objects.equals(coefficient.getValidTo(), newValidTo)) {
                continue; // no-op
            }
            if (newValidTo != null && closedOn.isAfter(today)) {
                errors.add(error(CoefficientActivationErrorCode.DATE_IN_FUTURE, coefficient));
                continue;
            }
            if (newValidTo != null && !newValidTo.isAfter(coefficient.getValidFrom())) {
                errors.add(error(CoefficientActivationErrorCode.CLOSURE_DATE_NOT_AFTER_ACTIVATION, coefficient));
                continue;
            }

            if (coefficient.getValidTo() != null) {
                // Correcting or reopening an existing close. One lookup answers two questions: is the
                // CURRENT validTo cascade-derived (nextRow starts exactly there -- undoing it here would
                // desynchronize it from the successor that derives it), and, independently, would the
                // REQUESTED new value (or infinity, for reopen) reach at/past that same nearest later
                // row -- which can happen even when the current validTo is NOT adjacent to it (e.g. an
                // explicitly-closed exit-case row later followed by an unrelated, non-adjacent
                // activation for the same supply). Both cases are rejected the same way: you may only
                // touch a valid_to you authored, and only up to where the timeline is actually free.
                Optional<SupplyPartitionCoefficient> nextRow = getCoefficientRepository.findNextActivatedAfter(
                        plantId, coefficient.getSupplyId(), coefficient.getId(), coefficient.getValidFrom());
                boolean currentValidToIsCascadeDerived = nextRow.isPresent()
                        && nextRow.get().getValidFrom().equals(coefficient.getValidTo());
                boolean requestedValueReachesNextRow = nextRow.isPresent()
                        && (newValidTo == null || newValidTo.isAfter(nextRow.get().getValidFrom()));
                if (currentValidToIsCascadeDerived || requestedValueReachesNextRow) {
                    errors.add(error(CoefficientActivationErrorCode.COEFFICIENT_HAS_SUCCESSOR, coefficient));
                    continue;
                }
            }
            writes.add(withValidFromTo(coefficient, coefficient.getValidFrom(), newValidTo));
        }

        if (!errors.isEmpty()) {
            throw new CoefficientActivationException(errors);
        }

        return applyAndRecompute(writes);
    }

    private SharingAgreement loadAgreement(UUID plantId, UUID sharingAgreementId) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        if (!agreement.getPlantId().equals(plantId)) {
            throw new SharingAgreementPlantMismatchException(sharingAgreementId, plantId);
        }
        // Deliberately NOT assertDraft(): see SharingAgreement#assertNotDraft() for why activation is
        // the one write on a non-DRAFT agreement that is correct.
        agreement.assertNotDraft();
        return agreement;
    }

    private Map<UUID, SupplyPartitionCoefficient> fetch(UUID sharingAgreementId, List<UUID> coefficientIds) {
        Map<UUID, SupplyPartitionCoefficient> byId = new LinkedHashMap<>();
        for (SupplyPartitionCoefficient coefficient :
                getCoefficientRepository.findAllByIdAndSharingAgreementId(coefficientIds, sharingAgreementId)) {
            byId.put(coefficient.getId(), coefficient);
        }
        return byId;
    }

    private List<UUID> distinct(List<UUID> coefficientIds) {
        return new ArrayList<>(new LinkedHashSet<>(coefficientIds));
    }

    private List<SupplyPartitionCoefficient> applyAndRecompute(List<SupplyPartitionCoefficient> writes) {
        List<SupplyPartitionCoefficient> touched = new ArrayList<>();
        Set<UUID> affectedAgreementIds = new LinkedHashSet<>();
        for (SupplyPartitionCoefficient write : writes) {
            touched.add(saveCoefficientRepository.save(write));
            affectedAgreementIds.add(write.getSharingAgreementId());
        }
        // Once per distinct affected agreement, never once per coefficient -- see
        // RecomputeSharingAgreementStatusRepository.
        for (UUID agreementId : affectedAgreementIds) {
            recomputeStatusRepository.recomputeStatus(agreementId);
        }
        return touched;
    }

    private SupplyPartitionCoefficient withValidFromTo(SupplyPartitionCoefficient coefficient,
                                                         Instant validFrom, Instant validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(coefficient.getId())
                .withSupplyId(coefficient.getSupplyId())
                .withPlantId(coefficient.getPlantId())
                .withSharingAgreementId(coefficient.getSharingAgreementId())
                .withCoefficient(coefficient.getCoefficient())
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(coefficient.getCreatedAt())
                .build();
    }

    private CoefficientActivationError error(CoefficientActivationErrorCode code, SupplyPartitionCoefficient coefficient) {
        return error(code, coefficient.getId(), coefficient.getSupplyId());
    }

    private CoefficientActivationError error(CoefficientActivationErrorCode code, UUID coefficientId, UUID supplyId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("coefficientId", coefficientId.toString());
        String cups = supplyId == null ? null : resolveCups(supplyId);
        if (cups != null) {
            params.put("cups", cups);
        }
        return new CoefficientActivationError(code, params);
    }

    private String resolveCups(UUID supplyId) {
        return getSupplyRepository.findById(SupplyId.of(supplyId)).map(Supply::getCode).orElse(null);
    }
}
