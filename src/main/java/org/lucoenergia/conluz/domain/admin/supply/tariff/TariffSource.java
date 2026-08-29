package org.lucoenergia.conluz.domain.admin.supply.tariff;

/**
 * Indicates the origin of the pricing data carried by a {@link TariffSegment},
 * so consumers can tell trustworthy contracted figures apart from approximations.
 *
 * <ul>
 *   <li>{@link #REAL_TARIFF} &mdash; the actual tariff contracted for the supply.</li>
 *   <li>{@link #ESTIMATE} &mdash; a computed approximation used when the real
 *       tariff is unavailable.</li>
 * </ul>
 */
public enum TariffSource {
    REAL_TARIFF,
    ESTIMATE
}
