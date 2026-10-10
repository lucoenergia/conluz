package org.lucoenergia.conluz.infrastructure.datadis;

import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Collaborators the unit tests of the scheduled Datadis jobs share.
 */
public final class DatadisJobTestSupport {

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private DatadisJobTestSupport() {
    }

    public static DatadisConfig enabledConfig(UUID communityId) {
        return new DatadisConfig.Builder()
                .setCommunityId(communityId).setEnabled(Boolean.TRUE)
                .setUsername("u").setPassword("p").build();
    }

    /**
     * A clock fixed at the instant, in UTC: the zone a job reads the date in must come from the
     * {@link ZoneResolver}, never from the clock.
     */
    public static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    public static ZoneResolver zoneResolver(ZoneId zone) {
        ZoneResolver zoneResolver = Mockito.mock(ZoneResolver.class);
        when(zoneResolver.resolveZoneIdForCommunity(any(UUID.class))).thenReturn(zone);
        return zoneResolver;
    }
}
