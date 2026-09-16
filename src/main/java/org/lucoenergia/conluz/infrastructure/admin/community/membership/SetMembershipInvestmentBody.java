package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(requiredProperties = {
        "investmentEur"
})
public class SetMembershipInvestmentBody {

    /**
     * Strictly greater than zero: a contribution of nothing is not an investment, and "no
     * investment recorded" is expressed by clearing the value rather than by writing a zero.
     * At most two decimals, because the column stores euros with cents and a third decimal would
     * be silently rounded away on write.
     */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 10, fraction = 2)
    @Schema(description = "The member's initial contribution to the community, in euros. Must be greater than zero, with at most two decimals.",
            example = "1500.00")
    private BigDecimal investmentEur;

    public BigDecimal getInvestmentEur() {
        return investmentEur;
    }

    public void setInvestmentEur(BigDecimal investmentEur) {
        this.investmentEur = investmentEur;
    }
}
