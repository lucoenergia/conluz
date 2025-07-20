package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.util.UUID;

/**
 * Exception thrown when a supply tariff is not found
 */
public class SupplyTariffNotFoundException extends RuntimeException {

    private final UUID supplyId;

    public SupplyTariffNotFoundException(UUID supplyId) {
        super("Supply tariff not found for supply with ID: " + supplyId);
        this.supplyId = supplyId;
    }

    public UUID getSupplyId() {
        return supplyId;
    }
}