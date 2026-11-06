package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.SupplyEnergyContribution;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommunityMonthlyEnergyBucketResponseTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-05-01T00:00:00+02:00");

    private final CommunityEnergySeriesBucket gap = CommunityEnergySeriesBucket.of(START, 3, List.of());

    @Test
    @DisplayName("EVL-001 CEM-006 a monthly bucket carries the first day of its month and rounded figures")
    void aMonthlyBucketCarriesItsFirstDayAndRoundedFigures() {
        CommunityMonthlyEnergyBucketResponse response = new CommunityMonthlyEnergyBucketResponse(
                CommunityEnergySeriesBucket.of(OffsetDateTime.parse("2026-05-01T00:00:00+02:00"), 3, List.of(
                        new SupplyEnergyContribution(0.1234, 1d), new SupplyEnergyContribution(0.2222, 1d))));

        assertEquals("2026/05/01", response.getDate());
        assertEquals("00:00", response.getTime());
        assertEquals(0.346, response.getSelfConsumptionKWh());
        assertEquals(3, response.getSupplyCount());
    }

    @Test
    @DisplayName("CEM-006 a gap keeps its null figures")
    void aGapKeepsItsNullFigures() {
        CommunityMonthlyEnergyBucketResponse response = new CommunityMonthlyEnergyBucketResponse(gap);

        assertNull(response.getSelfConsumptionKWh());
        assertNull(response.getSurplusKWh());
        assertEquals(0, response.getSuppliesWithData());
    }
}
