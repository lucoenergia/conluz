package org.lucoenergia.conluz.infrastructure.admin.supply.energytoday;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.GetSupplyEnergyTodayService;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.SupplyProductionToday;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.SupplyProductionTodayStatus;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientResolver;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSegment;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionDay;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshot;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetSupplyEnergyTodayServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** 2026-09-15T10:30 in Madrid, in a UTC clock: the local day must come from the community's zone. */
    private static final Instant NOW = Instant.parse("2026-09-15T08:30:00Z");
    private static final Instant YESTERDAY_START = Instant.parse("2026-09-13T22:00:00Z");
    private static final Instant TODAY_START = Instant.parse("2026-09-14T22:00:00Z");
    private static final Instant TOMORROW_START = Instant.parse("2026-09-15T22:00:00Z");

    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final CoefficientResolver coefficientResolver = mock(CoefficientResolver.class);
    private final GetHuaweiConfigRepository getHuaweiConfigRepository = mock(GetHuaweiConfigRepository.class);
    private final GetPlantRepository getPlantRepository = mock(GetPlantRepository.class);
    private final GetHuaweiProductionRepository getHuaweiProductionRepository =
            mock(GetHuaweiProductionRepository.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);

    private final GetSupplyEnergyTodayService service = new GetSupplyEnergyTodayServiceImpl(getSupplyRepository,
            coefficientResolver, getHuaweiConfigRepository, getPlantRepository, getHuaweiProductionRepository,
            zoneResolver, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Community community = CommunityMother.random().build();
    private final Supply supply = SupplyMother.random().withCommunity(community).build();
    private final Plant north = PlantMother.random(SupplyMother.random().withCommunity(community).build()).build();
    private final Plant south = PlantMother.random(SupplyMother.random().withCommunity(community).build()).build();
    private final Map<UUID, List<CoefficientSegment>> segments = new HashMap<>();

    @BeforeEach
    void givenTheSupplyAndItsPlants() {
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));
        when(zoneResolver.resolveZoneIdForCommunity(community.getId())).thenReturn(ZONE);
        when(coefficientResolver.resolveSegmentsBySupply(supply.getId(), TODAY_START, TOMORROW_START))
                .thenReturn(segments);
        for (Plant plant : List.of(north, south)) {
            when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));
        }
    }

    @Test
    @DisplayName("PIP-007 AC3 without a coefficient in force today the status is no coefficient today, whatever else is true")
    void withoutACoefficientTodayTheStatusIsNoCoefficientToday() {
        // A coefficient that only starts later today, in a plant with an enabled configuration and data.
        segments.put(north.getId(), List.of(segment(NOW.plusSeconds(60), TOMORROW_START, "0.1")));
        when(getHuaweiConfigRepository.isEnabled(north.getId())).thenReturn(true);
        givenSnapshots(north, NOW.minusSeconds(300), NOW);

        assertOnlyTheStatus(service.getToday(supply.getId()), SupplyProductionTodayStatus.NO_COEFFICIENT_TODAY);
        verifyNoInteractions(getHuaweiProductionRepository);
    }

    @Test
    @DisplayName("PIP-007 AC4 with coefficients only in plants without an enabled configuration the status is not available")
    void withoutAnEnabledConfigurationTheStatusIsNotAvailable() {
        segments.put(north.getId(), List.of(segment(TODAY_START, TOMORROW_START, "0.1")));
        segments.put(south.getId(), List.of(segment(TODAY_START, TOMORROW_START, "0.2")));
        when(getHuaweiConfigRepository.isEnabled(any())).thenReturn(false);

        assertOnlyTheStatus(service.getToday(supply.getId()), SupplyProductionTodayStatus.NOT_AVAILABLE);
        verifyNoInteractions(getHuaweiProductionRepository);
    }

    @Test
    @DisplayName("PIP-007 AC5 with an enabled configuration but no snapshot today in any plant the status is no data today")
    void withoutASnapshotTodayTheStatusIsNoDataToday() {
        segments.put(north.getId(), List.of(segment(TODAY_START, TOMORROW_START, "0.1")));
        segments.put(south.getId(), List.of(segment(TODAY_START, TOMORROW_START, "0.2")));
        when(getHuaweiConfigRepository.isEnabled(north.getId())).thenReturn(true);
        when(getHuaweiConfigRepository.isEnabled(south.getId())).thenReturn(false);
        when(getHuaweiProductionRepository.getProductionDay(north.getProviderCode(), YESTERDAY_START, TODAY_START,
                NOW)).thenReturn(new InverterProductionDay(87.6, List.of()));

        assertOnlyTheStatus(service.getToday(supply.getId()), SupplyProductionTodayStatus.NO_DATA_TODAY);
        verify(getHuaweiProductionRepository, never()).getProductionDay(south.getProviderCode(), YESTERDAY_START,
                TODAY_START, NOW);
    }

    @Test
    @DisplayName("PIP-007 AC6 PIP-010 an enabled plant without data is left out, and the plant with data is used alone")
    void anEnabledPlantWithoutDataIsLeftOut() {
        segments.put(north.getId(), List.of(segment(TODAY_START, TOMORROW_START, "0.1")));
        segments.put(south.getId(), List.of(segment(TODAY_START, TOMORROW_START, "0.2")));
        when(getHuaweiConfigRepository.isEnabled(any())).thenReturn(true);
        givenSnapshots(north, Instant.parse("2026-09-15T08:20:00Z"), Instant.parse("2026-09-15T08:25:00Z"));
        when(getHuaweiProductionRepository.getProductionDay(south.getProviderCode(), YESTERDAY_START, TODAY_START,
                NOW)).thenReturn(new InverterProductionDay(null, List.of()));

        SupplyProductionToday today = service.getToday(supply.getId());

        assertEquals(SupplyProductionTodayStatus.AVAILABLE, today.getStatus());
        // A single plant used: the coefficient is reported, not summed away.
        assertEquals(0.1, today.getPartitionCoefficient(), 1e-9);
        assertEquals(Instant.parse("2026-09-15T08:25:00Z"), today.getLastUpdatedAt());
        assertEquals(1.2 * 0.1, today.getAssignedEnergyKWh(), 1e-9);
        assertEquals(ZONE, today.getZone());
    }

    @Test
    @DisplayName("PIP-006 AC2 today is the local day of the supply's community, not the clock's")
    void todayIsTheLocalDayOfTheSupplysCommunity() {
        service.getToday(supply.getId());

        verify(coefficientResolver).resolveSegmentsBySupply(supply.getId(), TODAY_START, TOMORROW_START);
    }

    @Test
    @DisplayName("PIP-011 AC15 a supply that does not exist is not found")
    void aSupplyThatDoesNotExistIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(getSupplyRepository.findById(SupplyId.of(unknown))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> service.getToday(unknown));
    }

    private void givenSnapshots(Plant plant, Instant first, Instant last) {
        when(getHuaweiProductionRepository.getProductionDay(plant.getProviderCode(), YESTERDAY_START, TODAY_START,
                NOW)).thenReturn(new InverterProductionDay(null, List.of(
                new InverterSnapshot(first, 1.0), new InverterSnapshot(last, 1.2))));
    }

    private static CoefficientSegment segment(Instant from, Instant to, String coefficient) {
        return new CoefficientSegment(from, to, new BigDecimal(coefficient));
    }

    private static void assertOnlyTheStatus(SupplyProductionToday today, SupplyProductionTodayStatus status) {
        assertEquals(status, today.getStatus());
        assertNull(today.getAssignedEnergyKWh());
        assertNull(today.getAssignedPowerCurve());
        assertNull(today.getCurrentAssignedPowerKW());
        assertNull(today.getCurrentPlantPowerKW());
        assertNull(today.getPartitionCoefficient());
        assertNull(today.getLastUpdatedAt());
        assertNull(today.getStale());
        assertNull(today.getZone());
    }
}
