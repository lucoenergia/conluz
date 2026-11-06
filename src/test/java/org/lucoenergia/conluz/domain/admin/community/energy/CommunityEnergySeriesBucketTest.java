package org.lucoenergia.conluz.domain.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommunityEnergySeriesBucketTest {

    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-05-10T00:00:00+02:00");

    @Test
    @DisplayName("CEM-006 a bucket sums the supplies with data and reports how many they are")
    void aBucketSumsTheSuppliesWithData() {
        CommunityEnergySeriesBucket bucket = CommunityEnergySeriesBucket.of(STARTS_AT, 3, List.of(
                new SupplyEnergyContribution(0.1234, 1d), new SupplyEnergyContribution(0.2222, 2d)));

        assertEquals(STARTS_AT, bucket.getStartsAt());
        assertEquals(0.3456, bucket.getSelfConsumptionKWh(), 1e-12);
        assertEquals(3d, bucket.getSurplusKWh(), 1e-12);
        assertEquals(3, bucket.getSupplyCount());
        assertEquals(2, bucket.getSuppliesWithData());
    }

    @Test
    @DisplayName("CEM-006 a bucket without any supply with data is a gap: null figures, never 0")
    void aBucketWithoutAnySupplyWithDataIsAGap() {
        CommunityEnergySeriesBucket bucket = CommunityEnergySeriesBucket.of(STARTS_AT, 3, List.of());

        assertNull(bucket.getSelfConsumptionKWh());
        assertNull(bucket.getSurplusKWh());
        assertEquals(3, bucket.getSupplyCount());
        assertEquals(0, bucket.getSuppliesWithData());
    }

    @Test
    @DisplayName("CEM-006 a bucket whose supplies with data carry no energy is a measured zero")
    void aMeasuredZeroIsZero() {
        CommunityEnergySeriesBucket bucket = CommunityEnergySeriesBucket.of(STARTS_AT, 2, List.of(
                new SupplyEnergyContribution(0d, 0d), new SupplyEnergyContribution(0d, 0d)));

        assertEquals(0d, bucket.getSelfConsumptionKWh(), 0d);
        assertEquals(0d, bucket.getSurplusKWh(), 0d);
        assertEquals(2, bucket.getSuppliesWithData());
    }
}
