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
    void resolvesTheApplicationZoneFromTheConfiguredSetting() {
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));

        assertEquals(ZoneId.of("Europe/Madrid"), zoneResolver.resolveApplicationZoneId());
    }

    @Test
    void resolvesASupplysZoneFromTheConfiguredSetting() {
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));

        assertEquals(ZoneId.of("Europe/Madrid"), zoneResolver.resolveZoneIdForSupply(UUID.randomUUID()));
    }

    /**
     * Every lookup answers from the same application-level setting today. The day the zone becomes
     * a per-community one the supply and community lookups may diverge, but only inside this class.
     */
    @Test
    void everyLookupAnswersFromTheSameSettingToday() {
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("America/Bogota"));

        assertEquals(zoneResolver.resolveApplicationZoneId(),
                zoneResolver.resolveZoneIdForSupply(UUID.randomUUID()));
        assertEquals(zoneResolver.resolveApplicationZoneId(),
                zoneResolver.resolveZoneIdForCommunity(UUID.randomUUID()));
    }
}
