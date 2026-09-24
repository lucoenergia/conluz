package org.lucoenergia.conluz.infrastructure.shared.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.mockito.Mockito;

import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Pins the contract every consumer relies on: the zone comes from the configured
 * {@code conluz.time.zone.id} and not from the JVM default, and it does so through this one
 * class rather than through {@code TimeConfiguration} read directly.
 */
class ZoneResolverImplTest {

    private TimeConfiguration timeConfiguration;
    private ZoneResolver zoneResolver;

    @BeforeEach
    void setUp() {
        timeConfiguration = Mockito.mock(TimeConfiguration.class);
        zoneResolver = new ZoneResolverImpl(timeConfiguration);
    }

    @Test
    void resolvesAPlantsZoneFromTheConfiguredSetting() {
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));

        assertEquals(ZoneId.of("Europe/Madrid"), zoneResolver.resolveZoneId(UUID.randomUUID()));
    }

    @Test
    void resolvesASupplysZoneFromTheConfiguredSetting() {
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));

        assertEquals(ZoneId.of("Europe/Madrid"), zoneResolver.resolveZoneIdForSupply(UUID.randomUUID()));
    }

    /**
     * Both lookups answer from the same application-level setting today. The day the zone becomes
     * a per-community one they may diverge, but only inside this class.
     */
    @Test
    void bothLookupsAnswerFromTheSameSettingToday() {
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("America/Bogota"));

        assertEquals(zoneResolver.resolveZoneId(UUID.randomUUID()),
                zoneResolver.resolveZoneIdForSupply(UUID.randomUUID()));
    }
}
