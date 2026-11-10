package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture.EnergyCommunity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Writes the production of a plant the way the Datadis sync stores it: one record per hour Datadis
 * returns, the night hours, with no surplus, stored as 0.
 */
final class PlantEnergyTestSupport {

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** The production of every daylight hour, from 08:00 to 17:00 local. */
    static final float DAYLIGHT_KWH = 1.5f;
    /** A whole day of {@link #DAYLIGHT_KWH}: ten daylight hours. */
    static final double DAY_KWH = 10 * DAYLIGHT_KWH;

    private PlantEnergyTestSupport() {
    }

    /**
     * Every hour of the local day, 23, 24 or 25 of them, in order.
     */
    static List<Instant> hoursOf(LocalDate day) {
        Instant start = day.atStartOfDay(ZONE).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(ZONE).toInstant();
        return Stream.iterate(start, time -> time.isBefore(end), time -> time.plus(Duration.ofHours(1))).toList();
    }

    /**
     * A whole local day: {@link #DAYLIGHT_KWH} from 08:00 to 17:00 local, and 0 in the night hours.
     */
    static void writeDay(CommunityEnergyTestFixture fixture, Plant plant, LocalDate day) {
        writeDayWithout(fixture, plant, day, Set.of());
    }

    /**
     * A local day as {@link #writeDay}, without the hours at the given positions of {@link #hoursOf}.
     */
    static void writeDayWithout(CommunityEnergyTestFixture fixture, Plant plant, LocalDate day,
                                Set<Integer> missingHours) {
        List<Instant> hours = hoursOf(day);
        for (int position = 0; position < hours.size(); position++) {
            if (missingHours.contains(position)) {
                continue;
            }
            Instant hour = hours.get(position);
            int localHour = hour.atZone(ZONE).getHour();
            fixture.writeProduction(plant, hour, localHour >= 8 && localHour < 18 ? DAYLIGHT_KWH : 0f);
        }
    }

    /**
     * The first {@code count} hours of the local day, as {@link #writeDay} writes them.
     */
    static void writeFirstHours(CommunityEnergyTestFixture fixture, Plant plant, LocalDate day, int count) {
        List<Instant> hours = hoursOf(day);
        writeDayWithout(fixture, plant, day,
                Stream.iterate(count, position -> position < hours.size(), position -> position + 1)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    /**
     * PEM-004: no CUPS, supply id or member reference in a response body.
     */
    static void assertNoSupplyOrMember(String body, EnergyCommunity community) {
        for (Supply supply : community.allSupplies()) {
            assertFalse(body.contains(supply.getId().toString()), () -> "the body carries a supply id: " + body);
            assertFalse(body.contains(supply.getCode()), () -> "the body carries a CUPS: " + body);
        }
        for (User member : community.members()) {
            assertFalse(body.contains(member.getId().toString()), () -> "the body carries a user id: " + body);
            assertFalse(body.contains(member.getPersonalId()), () -> "the body carries a personal id: " + body);
        }
    }
}
