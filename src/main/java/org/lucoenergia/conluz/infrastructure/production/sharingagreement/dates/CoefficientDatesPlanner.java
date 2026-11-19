package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationException;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesOutcome;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesPlan;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesRequest;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementPlantMismatchException;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.activation.CoefficientActivationErrors;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads what {@link CoefficientDatesPlan} needs and runs it, for the write and for the preview alike, so both compute
 * the same effects from the same reads (SHA-014). Not transactional: it runs in the transaction of the service that
 * calls it, under the plant's lock for the write and without it for the preview.
 */
@Component
public class CoefficientDatesPlanner {

    private final GetSharingAgreementService getSharingAgreementService;
    private final GetSharingAgreementRepository getSharingAgreementRepository;
    private final GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    private final GetPlantRepository getPlantRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final ZoneResolver zoneResolver;
    private final CoefficientActivationErrors activationErrors;
    private final Clock clock;

    public CoefficientDatesPlanner(GetSharingAgreementService getSharingAgreementService,
                                   GetSharingAgreementRepository getSharingAgreementRepository,
                                   GetSupplyPartitionCoefficientRepository getCoefficientRepository,
                                   GetPlantRepository getPlantRepository,
                                   GetSupplyRepository getSupplyRepository,
                                   ZoneResolver zoneResolver,
                                   CoefficientActivationErrors activationErrors,
                                   Clock clock) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.getCoefficientRepository = getCoefficientRepository;
        this.getPlantRepository = getPlantRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.zoneResolver = zoneResolver;
        this.activationErrors = activationErrors;
        this.clock = clock;
    }

    /**
     * A plan that passed validation, with what the write needs to re-read its effects once written.
     *
     * @param agreements every agreement of the plant, by id, as it stood before the request
     */
    public record Prepared(CoefficientDatesPlan.Planned planned, UUID communityId,
                           Map<UUID, SharingAgreement> agreements) {
    }

    /**
     * @throws CoefficientActivationException with every invalid row, when any row is invalid
     */
    public Prepared plan(UUID plantId, UUID sharingAgreementId, CoefficientDatesRequest request) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        if (!agreement.getPlantId().equals(plantId)) {
            throw new SharingAgreementPlantMismatchException(sharingAgreementId, plantId);
        }
        // Not assertDraft(): dating coefficients is the write allowed on a published agreement, as activation is.
        agreement.assertNotDraft();
        Plant plant = getPlantRepository.findById(PlantId.of(plantId))
                .orElseThrow(() -> new PlantNotFoundException(PlantId.of(plantId)));
        UUID communityId = plant.getCommunity().getId();
        ZoneId zoneId = zoneResolver.resolveApplicationZoneId();

        Map<UUID, SharingAgreement> agreements = getSharingAgreementRepository.findByPlantId(plantId, null).stream()
                .collect(Collectors.toMap(SharingAgreement::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, SharingAgreementStatus> statuses = new LinkedHashMap<>();
        agreements.values().forEach(each -> statuses.put(each.getId(), each.getStatus()));

        CoefficientDatesPlan.Result result = CoefficientDatesPlan.plan(new CoefficientDatesPlan.Input(
                sharingAgreementId,
                getCoefficientRepository.findAllByPlantId(plantId),
                statuses,
                LocalDate.now(clock.withZone(zoneId)),
                zoneId,
                request,
                getCoefficientRepository.findEarliestValidFromByCommunityIdExcludingPlantId(communityId, plantId)
                        .orElse(null)));

        if (result instanceof CoefficientDatesPlan.Refused refused) {
            throw new CoefficientActivationException(refused.errors().stream().map(activationErrors::error).toList());
        }
        return new Prepared((CoefficientDatesPlan.Planned) result, communityId, agreements);
    }

    /**
     * The effects with the supplies and agreements they refer to, read in one query each.
     */
    public CoefficientDatesOutcome outcome(CoefficientDatesEffects effects, Map<UUID, SharingAgreement> agreements) {
        Set<UUID> supplyIds = Stream.of(
                        effects.coefficients().stream().map(CoefficientDatesEffects.CoefficientPeriodChange::supplyId),
                        effects.neighbours().stream().map(CoefficientDatesEffects.NeighbourChange::supplyId),
                        effects.leavingClosures().stream().map(CoefficientDatesEffects.LeavingClosureChange::supplyId))
                .flatMap(Function.identity())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Supply> supplies = supplyIds.isEmpty() ? Map.of() : getSupplyRepository.findAllByIds(supplyIds).stream()
                .collect(Collectors.toMap(Supply::getId, Function.identity()));
        Map<UUID, SharingAgreement> referenced = new LinkedHashMap<>();
        effects.agreements().forEach(change ->
                referenced.put(change.sharingAgreementId(), agreements.get(change.sharingAgreementId())));
        return new CoefficientDatesOutcome(effects, supplies, referenced);
    }
}
