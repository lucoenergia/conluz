package org.lucoenergia.conluz.domain.shared.time;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Resolves the time zone a plant's local dates (e.g. a coefficient's authored activation date)
 * are converted through. Parameterised by plant from the start even though today's implementation
 * ignores the argument and returns an application-level setting: the zone is expected to move to
 * community level (a coefficient is scoped to a plant, a plant belongs to a community), and this
 * single point is where that future change lands with no call site touched.
 */
public interface ZoneResolver {

    ZoneId resolveZoneId(UUID plantId);
}
