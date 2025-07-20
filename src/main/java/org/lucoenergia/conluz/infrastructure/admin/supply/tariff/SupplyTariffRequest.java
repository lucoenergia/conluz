package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;

import java.util.UUID;

/**
 * Request DTO for setting supply tariffs
 */
@Schema(requiredProperties = {
        "valley", "peak", "offPeak"
})
public class SupplyTariffRequest {

    @PositiveOrZero
    @NotNull
    @Schema(description = "Valley tariff rate in €/kWh", example = "0.12")
    private Double valley;

    @PositiveOrZero
    @NotNull
    @Schema(description = "Peak tariff rate in €/kWh", example = "0.18")
    private Double peak;

    @PositiveOrZero
    @NotNull
    @Schema(description = "Off-peak tariff rate in €/kWh", example = "0.14")
    private Double offPeak;

    public Double getValley() {
        return valley;
    }

    public void setValley(Double valley) {
        this.valley = valley;
    }

    public Double getPeak() {
        return peak;
    }

    public void setPeak(Double peak) {
        this.peak = peak;
    }

    public Double getOffPeak() {
        return offPeak;
    }

    public void setOffPeak(Double offPeak) {
        this.offPeak = offPeak;
    }

    /**
     * Maps this request to a SupplyTariff domain object
     *
     * @param supplyId the ID of the supply this tariff belongs to
     * @return the SupplyTariff domain object
     */
    public SupplyTariff mapToSupplyTariff(UUID supplyId) {
        return new SupplyTariff.Builder()
                .withSupply(new Supply.Builder().withId(supplyId).build())
                .withValley(valley)
                .withPeak(peak)
                .withOffPeak(offPeak)
                .build();
    }
}