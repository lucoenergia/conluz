package org.lucoenergia.conluz.infrastructure.production.sharingagreement.activation;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationError;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the per-item errors of a coefficient date write, with the params every
 * {@link CoefficientActivationErrorCode} documents. Runs in the transaction of the service that calls it.
 */
@Component
public class CoefficientActivationErrors {

    private final GetSupplyRepository getSupplyRepository;

    public CoefficientActivationErrors(GetSupplyRepository getSupplyRepository) {
        this.getSupplyRepository = getSupplyRepository;
    }

    public CoefficientActivationError error(CoefficientActivationErrorCode code, SupplyPartitionCoefficient coefficient) {
        return error(code, coefficient.getId(), coefficient.getSupplyId());
    }

    public CoefficientActivationError error(CoefficientActivationErrorCode code, UUID coefficientId, UUID supplyId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("coefficientId", coefficientId.toString());
        String cups = supplyId == null ? null : resolveCups(supplyId);
        if (cups != null) {
            params.put("cups", cups);
        }
        return new CoefficientActivationError(code, params);
    }

    public CoefficientActivationError errorWithConflict(SupplyPartitionCoefficient coefficient,
                                                        SupplyPartitionCoefficient conflictingWith) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("coefficientId", coefficient.getId().toString());
        String cups = resolveCups(coefficient.getSupplyId());
        if (cups != null) {
            params.put("cups", cups);
        }
        params.put("conflictingCoefficientId", conflictingWith.getId().toString());
        return new CoefficientActivationError(CoefficientActivationErrorCode.PERIOD_OVERLAP, params);
    }

    private String resolveCups(UUID supplyId) {
        return getSupplyRepository.findById(SupplyId.of(supplyId)).map(Supply::getCode).orElse(null);
    }
}
