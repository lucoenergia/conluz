package org.lucoenergia.conluz.infrastructure.production.huawei;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
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
 * Collaborators the unit tests of the scheduled Huawei aggregation jobs share.
 */
public final class HuaweiJobTestSupport {

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private HuaweiJobTestSupport() {
    }

    public static HuaweiConfig enabledConfig(UUID plantId) {
        return new HuaweiConfig.Builder()
                .setId(UUID.randomUUID())
                .setUsername("u").setPassword("p").setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .setPlantId(plantId)
                .build();
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
        when(zoneResolver.resolveZoneId(any(UUID.class))).thenReturn(zone);
        return zoneResolver;
    }
}
