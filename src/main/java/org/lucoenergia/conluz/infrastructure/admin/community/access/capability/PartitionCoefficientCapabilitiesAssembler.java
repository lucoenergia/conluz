package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the caller may do from each period of a list of partition coefficients.
 *
 * <p>{@code canReadSharingAgreement} is {@code SharingAgreementAccessPolicy.canReadThroughPlant},
 * which is what the guard {@code canReadSharingAgreement(plantId, sharingAgreementId)} behind
 * {@code GET /plants/{plantId}/sharing-agreements/{sharingAgreementId}} delegates to, so a link that
 * is shown can never be refused. It lives on the period because {@code sharingAgreement} is a
 * reference, and references carry no capabilities.</p>
 *
 * <p>The rule needs the plant (its community is reached through its supply) and the agreement (for
 * its plant), while a period carries only their ids. Every distinct plant and agreement of the list
 * is loaded in one query each, and the rule is evaluated once per distinct pair, so the cost does
 * not depend on the number of periods. A plant or agreement that no longer resolves is
 * {@code null} to the policy, which answers "not visible" -- {@code false}, never an exception.
 * The plant is the policy's input, not the supply's community: a plant can be moved to a supply of
 * another community, taking its coefficients with it.</p>
 */
@Component
public class PartitionCoefficientCapabilitiesAssembler {

    private final AccessPolicies policies;
    private final GetPlantRepository getPlantRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;

    public PartitionCoefficientCapabilitiesAssembler(AccessPolicies policies, GetPlantRepository getPlantRepository,
                                                     GetSharingAgreementRepository getSharingAgreementRepository) {
        this.policies = policies;
        this.getPlantRepository = getPlantRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
    }

    /**
     * The capabilities of every period, keyed by coefficient id.
     *
     * <p>Coefficient ids are primary keys, and every list handed here -- a query result, or the rows
     * a single write touched -- holds each row at most once, so the key is unique within one
     * response. A repeated id is refused rather than collapsed: collapsing would hand one period
     * another's answer without anyone noticing.</p>
     *
     * @throws IllegalStateException if two details share a coefficient id
     */
    public Map<UUID, PartitionCoefficientCapabilitiesResponse> assembleAll(User caller,
                                                                         List<SupplyPartitionCoefficientDetail> details) {
        if (details.isEmpty()) {
            return Map.of();
        }

        Set<PlantId> plantIds = details.stream()
                .map(detail -> PlantId.of(detail.getPlant().id()))
                .collect(Collectors.toSet());
        Set<UUID> agreementIds = details.stream()
                .map(detail -> detail.getSharingAgreement().id())
                .collect(Collectors.toSet());
        Map<UUID, Plant> plantsById = getPlantRepository.findAllByIds(plantIds).stream()
                .collect(Collectors.toMap(Plant::getId, Function.identity()));
        Map<UUID, SharingAgreement> agreementsById = getSharingAgreementRepository.findAllByIdsWithoutFile(agreementIds)
                .stream()
                .collect(Collectors.toMap(SharingAgreement::getId, Function.identity()));

        Map<List<UUID>, PartitionCoefficientCapabilitiesResponse> byPlantAndAgreement = new HashMap<>();
        Map<UUID, PartitionCoefficientCapabilitiesResponse> byCoefficientId = new LinkedHashMap<>();
        for (SupplyPartitionCoefficientDetail detail : details) {
            UUID plantId = detail.getPlant().id();
            UUID agreementId = detail.getSharingAgreement().id();
            PartitionCoefficientCapabilitiesResponse capabilities = byPlantAndAgreement.computeIfAbsent(
                    List.of(plantId, agreementId),
                    key -> assemble(caller, plantsById.get(plantId), agreementsById.get(agreementId)));
            if (byCoefficientId.putIfAbsent(detail.getId(), capabilities) != null) {
                throw new IllegalStateException("Coefficient " + detail.getId()
                        + " appears twice in one response; its capabilities would be ambiguous");
            }
        }
        return byCoefficientId;
    }

    private PartitionCoefficientCapabilitiesResponse assemble(User caller, Plant plant, SharingAgreement agreement) {
        return PartitionCoefficientCapabilitiesResponse.builder()
                .withCanReadSharingAgreement(policies.sharingAgreement()
                        .canReadThroughPlant(caller, plant, agreement).isAllowed())
                .build();
    }
}
