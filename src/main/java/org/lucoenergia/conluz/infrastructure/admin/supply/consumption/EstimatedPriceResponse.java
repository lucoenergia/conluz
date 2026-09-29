package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.EstimatedPrice;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The estimated energy-term price a savings figure was priced with.
 *
 * <p>An object with a single field rather than a bare number, so attributes can be added to it
 * later without changing the type of the field that holds it. Its nullability lives on the field
 * that references it, never inside: when this object exists, its price holds a value.
 *
 * <p>The price is returned exactly as configured, unrounded: it is a rate, not an amount.
 */
@Schema(requiredProperties = {"eurPerKWh"})
public class EstimatedPriceResponse {

    @Schema(description = "Estimated price of the energy term, in euros per kWh, before taxes. " +
            "The power term, access tolls, charges, electricity tax and VAT are not included. " +
            "Returned exactly as configured, without rounding.",
            example = "0.15")
    private final BigDecimal eurPerKWh;

    public EstimatedPriceResponse(EstimatedPrice estimatedPrice) {
        this.eurPerKWh = Objects.requireNonNull(estimatedPrice).getEurPerKWh();
    }

    /**
     * The response for a price that may be absent: null when there is none, so that an absent price
     * is an absent object rather than an object holding a null.
     */
    public static EstimatedPriceResponse from(EstimatedPrice estimatedPrice) {
        return estimatedPrice == null ? null : new EstimatedPriceResponse(estimatedPrice);
    }

    public BigDecimal getEurPerKWh() {
        return eurPerKWh;
    }
}
