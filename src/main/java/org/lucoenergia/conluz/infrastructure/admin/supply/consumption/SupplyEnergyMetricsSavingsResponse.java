package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What the self-consumed energy of the period was worth, and where the prices behind that figure
 * came from.
 *
 * <p>This is where the amount is rounded, once, for presentation. The calculation itself keeps
 * full precision, so a period cut into several tariff segments is rounded as a whole rather than
 * once per segment.
 */
@Schema(requiredProperties = {"amountEur", "tariffSource"})
public class SupplyEnergyMetricsSavingsResponse {

    @Schema(description = "Estimated amount saved over the period, in euros, rounded to cents. " +
            "Null when no period could be resolved, which is not the same as a saving of zero.",
            example = "72.75", types = {"number", "null"})
    private final BigDecimal amountEur;
    @Schema(description = "Whether the prices behind the amount are the supply's contracted " +
            "tariff or an estimate. A single estimated stretch of the period makes the whole " +
            "amount an estimate.",
            example = "ESTIMATE")
    private final TariffSource tariffSource;

    public SupplyEnergyMetricsSavingsResponse(SupplySavings savings) {
        // Scale is declared inline rather than as a constant: ResponseSchemaNullabilityArchTest
        // inspects every field of a *Response class, static ones included, and would demand a
        // @Schema marker on a private constant that is not part of the contract at all.
        int cents = 2;
        this.amountEur = savings.getAmountEur() == null
                ? null
                : savings.getAmountEur().setScale(cents, RoundingMode.HALF_UP);
        this.tariffSource = savings.getTariffSource();
    }

    public BigDecimal getAmountEur() {
        return amountEur;
    }

    public TariffSource getTariffSource() {
        return tariffSource;
    }
}
