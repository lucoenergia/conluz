package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.time.Instant;

/**
 * One real-time snapshot of a plant's inverter: the instant it was taken, and the energy the inverter has counted
 * since its counter last reset at local midnight ({@code day_power}), in kWh.
 */
public record InverterSnapshot(Instant time, double dayPowerKWh) {
}
