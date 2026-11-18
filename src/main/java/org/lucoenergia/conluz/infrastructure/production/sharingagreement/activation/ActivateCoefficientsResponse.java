package org.lucoenergia.conluz.infrastructure.production.sharingagreement.activation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PartitionCoefficientCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient.PartitionCoefficientResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Schema(requiredProperties = {"coefficients", "leavingClosures"})
public class ActivateCoefficientsResponse {

    @Schema(description = "Every coefficient actually touched by this call -- the requested targets " +
            "and any predecessor cascaded as a result, plus the leavingClosures. Empty when the whole batch was a no-op.")
    private final List<PartitionCoefficientResponse> coefficients;

    @Schema(description = "The subset of coefficients closed because their supply is absent from the agreement: " +
            "on the agreement's first activation, the coefficient in force on appliedOn of every supply of the " +
            "plant that has no coefficient in it, closed on appliedOn. Empty on any other activation.")
    private final List<PartitionCoefficientResponse> leavingClosures;

    public ActivateCoefficientsResponse(List<SupplyPartitionCoefficientDetail> touched, Set<UUID> leavingClosureIds,
                                        Map<UUID, PartitionCoefficientCapabilitiesResponse> capabilitiesById) {
        this.coefficients = touched.stream()
                .map(detail -> new PartitionCoefficientResponse(detail, capabilitiesById.get(detail.getId())))
                .toList();
        this.leavingClosures = coefficients.stream()
                .filter(coefficient -> leavingClosureIds.contains(coefficient.getId()))
                .toList();
    }

    public List<PartitionCoefficientResponse> getCoefficients() {
        return coefficients;
    }

    public List<PartitionCoefficientResponse> getLeavingClosures() {
        return leavingClosures;
    }
}
