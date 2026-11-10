package org.lucoenergia.conluz.domain.production.plant.energy;

import java.time.OffsetDateTime;
import java.time.Period;
import java.util.UUID;

/**
 * Resolves the backing supply and the period the energy figures of a plant are computed over, so that
 * every operation reporting on a plant agrees on both.
 */
public interface PlantEnergyScopeResolver {

    /**
     * The plant's backing supply, its community's time zone, and the period resolved for it.
     *
     * <p>The period is resolved in one of three ways: the explicit {@code startDate}, inclusive, and
     * {@code endDate}, exclusive, with an end after the start of the current hour clipped to it; the
     * {@code referencePeriod}; or, when neither is supplied, from the first to the last production
     * record stored for the plant. An explicit period always resolves.</p>
     *
     * @param referencePeriod the reference period to resolve, or null when none is requested
     * @param maxLength       the longest explicit period the operation accepts
     * @throws org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException when the plant does
     *         not exist
     * @throws org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException when the
     *         requested period breaks one of the rules of
     *         {@link org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator}
     */
    PlantEnergyScope resolve(UUID plantId, OffsetDateTime startDate, OffsetDateTime endDate,
                             PlantEnergyMetricsReferencePeriod referencePeriod, Period maxLength);
}
