package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;

import java.util.UUID;

/**
 * Response DTO for supply tariffs
 */
public class SupplyTariffResponse {

    @Schema(description = "Internal unique identifier of the supply tariff", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID id;

    @Schema(description = "Supply this tariff belongs to")
    private final SupplyResponse supply;

    @Schema(description = "Valley tariff rate in €/kWh", example = "0.12")
    private final Double valley;

    @Schema(description = "Peak tariff rate in €/kWh", example = "0.18")
    private final Double peak;

    @Schema(description = "Off-peak tariff rate in €/kWh", example = "0.14")
    private final Double offPeak;

    public SupplyTariffResponse(SupplyTariff supplyTariff) {
        this.id = supplyTariff.getId();
        this.supply = supplyTariff.getSupply() != null ? new SupplyResponse(supplyTariff.getSupply()) : null;
        this.valley = supplyTariff.getValley();
        this.peak = supplyTariff.getPeak();
        this.offPeak = supplyTariff.getOffPeak();
    }

    public UUID getId() {
        return id;
    }

    public SupplyResponse getSupply() {
        return supply;
    }

    public Double getValley() {
        return valley;
    }

    public Double getPeak() {
        return peak;
    }

    public Double getOffPeak() {
        return offPeak;
    }
}