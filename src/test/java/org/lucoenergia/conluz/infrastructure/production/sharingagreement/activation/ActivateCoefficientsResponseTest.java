package org.lucoenergia.conluz.infrastructure.production.sharingagreement.activation;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetailMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient.PartitionCoefficientResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActivateCoefficientsResponseTest {

    @Test
    void leavingClosuresAreTheSubsetOfCoefficientsNamedAsClosures() {
        SupplyPartitionCoefficientDetail activated = SupplyPartitionCoefficientDetailMother.random(BigDecimal.ONE,
                Instant.parse("2025-06-01T00:00:00Z"), null);
        SupplyPartitionCoefficientDetail cascaded = SupplyPartitionCoefficientDetailMother.random(BigDecimal.ONE,
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"));
        SupplyPartitionCoefficientDetail leaving = SupplyPartitionCoefficientDetailMother.random(BigDecimal.ONE,
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"));

        ActivateCoefficientsResponse response = new ActivateCoefficientsResponse(
                List.of(activated, cascaded, leaving), Set.of(leaving.getId()), Map.of());

        assertThat(response.getCoefficients()).extracting(PartitionCoefficientResponse::getId)
                .containsExactly(activated.getId(), cascaded.getId(), leaving.getId());
        assertThat(response.getLeavingClosures()).extracting(PartitionCoefficientResponse::getId)
                .containsExactly(leaving.getId());
    }

    @Test
    void leavingClosuresAreEmptyWhenNothingWasClosed() {
        SupplyPartitionCoefficientDetail activated = SupplyPartitionCoefficientDetailMother.random(BigDecimal.ONE,
                Instant.parse("2025-06-01T00:00:00Z"), null);

        ActivateCoefficientsResponse response = new ActivateCoefficientsResponse(List.of(activated), Set.of(),
                Map.of());

        assertThat(response.getCoefficients()).hasSize(1);
        assertThat(response.getLeavingClosures()).isEmpty();
    }
}
