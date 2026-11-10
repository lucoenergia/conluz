package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.inverter.GetPlantInverterProductionTodayService;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionDay;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionStatus;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshot;
import org.lucoenergia.conluz.domain.production.plant.inverter.PlantInverterProductionToday;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetPlantInverterProductionTodayServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** 2026-09-15T10:30 in Madrid, in a UTC clock: the local day must come from the community's zone. */
    private static final Instant NOW = Instant.parse("2026-09-15T08:30:00Z");
    private static final Instant YESTERDAY_START = Instant.parse("2026-09-13T22:00:00Z");
    private static final Instant TODAY_START = Instant.parse("2026-09-14T22:00:00Z");

    private final GetPlantRepository getPlantRepository = mock(GetPlantRepository.class);
    private final GetHuaweiConfigRepository getHuaweiConfigRepository = mock(GetHuaweiConfigRepository.class);
    private final GetHuaweiProductionRepository getHuaweiProductionRepository =
            mock(GetHuaweiProductionRepository.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);

    private final GetPlantInverterProductionTodayService service = new GetPlantInverterProductionTodayServiceImpl(
            getPlantRepository, getHuaweiConfigRepository, getHuaweiProductionRepository, zoneResolver,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final Community community = CommunityMother.random().build();
    private final Plant plant = PlantMother.random(SupplyMother.random().withCommunity(community).build()).build();

    @BeforeEach
    void givenThePlant() {
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));
        when(zoneResolver.resolveZoneIdForCommunity(community.getId())).thenReturn(ZONE);
    }

    @Test
    @DisplayName("PIP-001 AC2 without an enabled configuration the status is not available and InfluxDB is not read")
    void withoutAnEnabledConfigurationNothingIsRead() {
        when(getHuaweiConfigRepository.isEnabled(plant.getId())).thenReturn(false);

        PlantInverterProductionToday today = service.getToday(plant.getId());

        assertEquals(InverterProductionStatus.NOT_AVAILABLE, today.getStatus());
        assertNull(today.getTodayEnergyKWh());
        verifyNoInteractions(getHuaweiProductionRepository);
    }

    @Test
    @DisplayName("PIP-001 AC1 the station's snapshots of the community's local day and yesterday's last are read")
    void theSnapshotsOfTheCommunitysLocalDayAreRead() {
        when(getHuaweiConfigRepository.isEnabled(plant.getId())).thenReturn(true);
        when(getHuaweiProductionRepository.getProductionDay(plant.getProviderCode(), YESTERDAY_START, TODAY_START,
                NOW)).thenReturn(new InverterProductionDay(323.36, List.of(
                new InverterSnapshot(Instant.parse("2026-09-14T22:00:00.980Z"), 323.36),
                new InverterSnapshot(Instant.parse("2026-09-14T22:05:00Z"), 0),
                new InverterSnapshot(Instant.parse("2026-09-15T08:00:00Z"), 1.5))));

        PlantInverterProductionToday today = service.getToday(plant.getId());

        assertEquals(InverterProductionStatus.AVAILABLE, today.getStatus());
        assertEquals(1.5, today.getTodayEnergyKWh());
        assertEquals(2, today.getPowerCurve().size());
        assertEquals(Instant.parse("2026-09-15T08:00:00Z"), today.getLastUpdatedAt());
        assertEquals(false, today.getStale());
        assertEquals(ZONE, today.getZone());
    }

    @Test
    @DisplayName("PIP-001 AC3 an enabled configuration without a snapshot today is no data today")
    void anEnabledConfigurationWithoutASnapshotTodayIsNoDataToday() {
        when(getHuaweiConfigRepository.isEnabled(plant.getId())).thenReturn(true);
        when(getHuaweiProductionRepository.getProductionDay(plant.getProviderCode(), YESTERDAY_START, TODAY_START,
                NOW)).thenReturn(new InverterProductionDay(12.5, List.of()));

        PlantInverterProductionToday today = service.getToday(plant.getId());

        assertEquals(InverterProductionStatus.NO_DATA_TODAY, today.getStatus());
        assertNull(today.getTodayEnergyKWh());
        assertNull(today.getLastUpdatedAt());
    }

    @Test
    @DisplayName("PIP-004 AC10 a latest snapshot 61 minutes old is stale")
    void aLatestSnapshotSixtyOneMinutesOldIsStale() {
        when(getHuaweiConfigRepository.isEnabled(plant.getId())).thenReturn(true);
        when(getHuaweiProductionRepository.getProductionDay(plant.getProviderCode(), YESTERDAY_START, TODAY_START,
                NOW)).thenReturn(new InverterProductionDay(null, List.of(
                new InverterSnapshot(NOW.minusSeconds(66 * 60), 1.0),
                new InverterSnapshot(NOW.minusSeconds(61 * 60), 1.1))));

        assertEquals(true, service.getToday(plant.getId()).getStale());
    }

    @Test
    @DisplayName("PIP-005 an unknown plant is not found")
    void anUnknownPlantIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(unknown))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () -> service.getToday(unknown));
        verifyNoInteractions(getHuaweiProductionRepository);
    }
}
