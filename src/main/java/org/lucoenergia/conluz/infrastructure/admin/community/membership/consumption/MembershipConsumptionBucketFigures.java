package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipConsumptionTotals;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The figures of one bucket of a membership's consumption series as every membership series
 * presents them: each energy figure rounded once, to 3 decimals, on the unrounded sum, and the
 * savings rounded once, to cents. Shared by the monthly and the daily bucket responses so that the
 * two round alike.
 */
final class MembershipConsumptionBucketFigures {

    private static final int CENTS = 2;

    private final BigDecimal gridImportKWh;
    private final BigDecimal surplusKWh;
    private final BigDecimal selfConsumptionKWh;
    private final BigDecimal assignedProductionKWh;
    private final BigDecimal savingsEur;
    private final TariffSource tariffSource;
    private final int supplyCount;
    private final int suppliesWithData;

    MembershipConsumptionBucketFigures(MembershipConsumptionTotals totals) {
        this.gridImportKWh = rounded(totals.getConsumptionKWh().doubleValue());
        this.surplusKWh = rounded(totals.getSurplusEnergyKWh().doubleValue());
        this.selfConsumptionKWh = rounded(totals.getSelfConsumptionEnergyKWh().doubleValue());
        this.assignedProductionKWh = rounded(totals.getAssignedProductionKWh());
        Optional<SupplySavings> savings = totals.getSavings();
        this.savingsEur = savings.map(s -> s.getAmountEur().setScale(CENTS, RoundingMode.HALF_UP)).orElse(null);
        this.tariffSource = savings.map(SupplySavings::getTariffSource).orElse(null);
        this.supplyCount = totals.getSupplyCount();
        this.suppliesWithData = totals.getSuppliesWithData();
    }

    private static BigDecimal rounded(double kWh) {
        return BigDecimal.valueOf(KWhRounding.round(kWh));
    }

    BigDecimal gridImportKWh() {
        return gridImportKWh;
    }

    BigDecimal surplusKWh() {
        return surplusKWh;
    }

    BigDecimal selfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    BigDecimal assignedProductionKWh() {
        return assignedProductionKWh;
    }

    BigDecimal savingsEur() {
        return savingsEur;
    }

    TariffSource tariffSource() {
        return tariffSource;
    }

    int supplyCount() {
        return supplyCount;
    }

    int suppliesWithData() {
        return suppliesWithData;
    }
}
