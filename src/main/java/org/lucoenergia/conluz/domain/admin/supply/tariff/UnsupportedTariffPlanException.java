package org.lucoenergia.conluz.domain.admin.supply.tariff;

/**
 * Raised when a consumer is asked to price a {@link TariffSegment} whose {@link TariffPlan} it
 * does not know how to price.
 *
 * <p>{@link TariffPlan} is not a sealed type, and {@link TimeOfUsePlan} is a placeholder that
 * carries no rates at all, so a plan reaching a consumer that cannot price it is reachable today
 * and will stay reachable as plans are added. Failing loudly is deliberate: the alternative --
 * skipping the segment -- prices that stretch of the period at zero and reports a monetary figure
 * that is wrong by exactly the amount nobody can see.
 *
 * <p>This signals a server-side gap between the resolver and its consumer, never bad input, so it
 * must not be mapped to a 4xx.
 */
public class UnsupportedTariffPlanException extends RuntimeException {

    private final transient Class<? extends TariffPlan> planType;

    public UnsupportedTariffPlanException(Class<? extends TariffPlan> planType) {
        super(String.format("Tariff plan of type %s cannot be priced. Only %s is supported.",
                planType.getSimpleName(), FlatPlan.class.getSimpleName()));
        this.planType = planType;
    }

    public Class<? extends TariffPlan> getPlanType() {
        return planType;
    }
}
