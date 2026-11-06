package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.SupplyEnergyContribution;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommunityDailyEnergyBucketResponseTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-05-10T00:00:00+02:00");

    private final CommunityEnergySeriesBucket gap = CommunityEnergySeriesBucket.of(START, 3, List.of());

    @Test
    @DisplayName("EVL-001 CEM-006 a daily bucket carries its local date and rounded figures")
    void aDailyBucketCarriesItsDateAndRoundedFigures() {
        CommunityDailyEnergyBucketResponse response = new CommunityDailyEnergyBucketResponse(
                CommunityEnergySeriesBucket.of(OffsetDateTime.parse("2026-05-10T00:00:00+02:00"), 3, List.of(
                        new SupplyEnergyContribution(0.1234, 1d), new SupplyEnergyContribution(0.2222, 1d))));

        assertEquals("2026/05/10", response.getDate());
        assertEquals("00:00", response.getTime());
        assertEquals(0.346, response.getSelfConsumptionKWh());
        assertEquals(2.0, response.getSurplusKWh());
        assertEquals(2, response.getSuppliesWithData());
    }

    @Test
    @DisplayName("CEM-006 a gap keeps its null figures")
    void aGapKeepsItsNullFigures() {
        CommunityDailyEnergyBucketResponse response = new CommunityDailyEnergyBucketResponse(gap);

        assertNull(response.getSelfConsumptionKWh());
        assertNull(response.getSurplusKWh());
        assertEquals(0, response.getSuppliesWithData());
    }
}
