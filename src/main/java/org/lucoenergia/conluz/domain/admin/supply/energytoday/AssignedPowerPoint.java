package org.lucoenergia.conluz.domain.admin.supply.energytoday;

import java.time.Instant;

/**
 * One fixed 5-minute slot of the power assigned to a supply point: the assigned energy of the slot over its 5
 * minutes, in kW, unrounded, at the instant the slot ends. The power is null when it is not known for the whole slot.
 */
public record AssignedPowerPoint(Instant endsAt, Double powerKW) {
}
