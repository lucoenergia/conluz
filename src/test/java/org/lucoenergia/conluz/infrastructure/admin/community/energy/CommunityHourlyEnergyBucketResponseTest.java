package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.SupplyEnergyContribution;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommunityHourlyEnergyBucketResponseTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-25T02:00:00+01:00");

    private final CommunityEnergySeriesBucket bucket = CommunityEnergySeriesBucket.of(START, 3, List.of(
            new SupplyEnergyContribution(0.1234, 1.0005), new SupplyEnergyContribution(0.2222, 0d)));
    private final CommunityEnergySeriesBucket gap = CommunityEnergySeriesBucket.of(START, 3, List.of());

    @Test
    @DisplayName("EVL-001 CEM-006 an hourly bucket carries its start instant, local label and rounded figures")
    void anHourlyBucketCarriesItsStartInstantLabelAndRoundedFigures() {
        CommunityHourlyEnergyBucketResponse response = new CommunityHourlyEnergyBucketResponse(bucket);

        assertEquals(START, response.getStartsAt());
        assertEquals("2026/10/25", response.getDate());
        assertEquals("02:00", response.getTime());
        assertEquals(0.346, response.getSelfConsumptionKWh());
        assertEquals(1.001, response.getSurplusKWh());
        assertEquals(3, response.getSupplyCount());
        assertEquals(2, response.getSuppliesWithData());
    }

    @Test
    @DisplayName("CEM-006 a gap keeps its null figures")
    void aGapKeepsItsNullFigures() {
        CommunityHourlyEnergyBucketResponse response = new CommunityHourlyEnergyBucketResponse(gap);

        assertNull(response.getSelfConsumptionKWh());
        assertNull(response.getSurplusKWh());
        assertEquals(0, response.getSuppliesWithData());
    }
}
