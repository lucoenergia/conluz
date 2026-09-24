/**
 * Tariff model: what a kWh costs a supply point over a period of time.
 *
 * <p>This package records decisions that are not recoverable from the code alone. They bind
 * every implementation of {@link org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffResolver}
 * and every consumer of the schedules it returns.
 *
 * <h2>1. Price excludes taxes; VAT travels with the segment</h2>
 *
 * <p>{@link org.lucoenergia.conluz.domain.admin.supply.tariff.FlatPlan#getPricePerKwh()} is the
 * energy term per kWh <em>before</em> tax -- the taxable base. VAT is never folded into it; it
 * is carried separately by each
 * {@link org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSegment}, so a rate change
 * mid-period is expressed by splitting the timeline rather than by rewriting prices.
 *
 * <p>The estimated price is currently <em>global</em>, not per supply point: it is configured
 * through {@code conluz.supply.tariff.estimated.*} and ships as 0.15 &euro;/kWh with a VAT rate
 * of 0. A per-supply-point price is anticipated; it lands behind the existing
 * {@code SupplyId} parameter of
 * {@link org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffResolver#scheduleFor}
 * without changing the port.
 *
 * <h2>2. The source of a price must reach the reader</h2>
 *
 * <p>{@link org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource} must propagate to
 * <strong>any HTTP response that shows a monetary amount</strong>. A figure derived from an
 * estimate and a figure derived from a contracted tariff are not interchangeable, and the
 * caller cannot tell them apart unless the response says so.
 *
 * <h2>3. Dates are civil dates, converted once</h2>
 *
 * <p>Tariff dates are {@code LocalDate} civil dates interpreted in the configured time zone
 * ({@code conluz.time.zone.id}), and
 * {@link org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange} is half-open:
 * {@code [start, end)}.
 *
 * <ul>
 *   <li>An <em>inclusive</em> public end date maps to the civil date containing it
 *       <strong>plus one day</strong> as the exclusive {@code DateRange} end. "All of 2025" is
 *       {@code [2025-01-01, 2026-01-01)}, never {@code [2025-01-01, 2025-12-31)}.</li>
 *   <li>A {@code DateRange} bound converts to an instant as {@code date.atStartOfDay(zone)}.</li>
 *   <li>That conversion happens <strong>once</strong>, at the top of the consuming service, so
 *       every downstream call shares one instant set.
 *       {@code GetProductionServiceImpl#exclusiveTo} is the precedent for that <em>shape</em>
 *       only -- it is not the conversion to reuse, since it takes an {@code OffsetDateTime} and
 *       nudges it by a nanosecond.</li>
 * </ul>
 *
 * <h2>4. The zone comes from ZoneResolver, never from TimeConfiguration</h2>
 *
 * <p>Consumers obtain the zone through
 * {@link org.lucoenergia.conluz.domain.shared.time.ZoneResolver}, as
 * {@code CoefficientActivationServiceImpl} does -- never by reading
 * {@code TimeConfiguration} directly. The zone is expected to move from an application-level
 * setting to a per-community one, and {@code ZoneResolver} is the single place that change
 * lands. The {@code valid_from} column remark added by
 * {@code add_supply_partition_coefficient_valid_from_zone_remark.xml} spells out the cost of
 * getting this wrong for data that is already persisted.
 *
 * <h2>5. An accepted exception, and when it expires</h2>
 *
 * <p>Tariffs are not persisted today, so the temporal invariants are enforced in memory, by
 * {@link org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSchedule} and
 * {@link org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange} constructors. This is a
 * deliberate exception with a written expiry: <strong>when tariffs gain persistence</strong>,
 * {@code Instant} versus {@code LocalDate} must be re-evaluated, and ADR-0001's pattern must be
 * applied in full -- a deferrable exclusion constraint <em>plus</em> an explicit
 * in-transaction re-check. Both halves, never one: deferring alone moves the failure to commit
 * time, where no service code can catch it.
 *
 * <h2>6. Forbidden states</h2>
 *
 * <p>Empty schedules and empty ranges are rejected at construction. A schedule must cover the
 * requested range completely, with no gaps and no overlaps, so "no tariff known for this
 * period" cannot be signalled by returning nothing: consumers must model it as explicit
 * absence.
 *
 * <h2>Out of scope</h2>
 *
 * <p>This model covers the energy term and VAT only. Electricity tax, access tolls, charges,
 * the power term, surpluses and time-of-use discrimination are all deliberately absent;
 * {@link org.lucoenergia.conluz.domain.admin.supply.tariff.TimeOfUsePlan} is a placeholder
 * carrying no rates.
 *
 * <p>ADR-0001 referenced above is
 * {@code docs/architecture/adr/0001-deferred-exclusion-constraint-for-coefficient-periods.md}.
 */
package org.lucoenergia.conluz.domain.admin.supply.tariff;
