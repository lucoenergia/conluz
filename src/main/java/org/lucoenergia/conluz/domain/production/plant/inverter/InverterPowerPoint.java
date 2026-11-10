package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.time.Instant;

/**
 * One point of the inverter's power curve: the average power over the interval between two consecutive snapshots,
 * in kW, at the instant of the later one. The power is null when the interval is a gap.
 */
public record InverterPowerPoint(Instant time, Double powerKW) {
}
