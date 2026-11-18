package org.lucoenergia.conluz.domain.production.sharingagreement.activation;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoefficientActivationResultTest {

    @Test
    void keepsACopyOfTheListsItIsGiven() {
        SupplyPartitionCoefficient row = new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withCoefficient(BigDecimal.ONE)
                .withCreatedAt(Instant.EPOCH)
                .build();
        List<SupplyPartitionCoefficient> touched = new ArrayList<>(List.of(row));
        List<SupplyPartitionCoefficient> leavingClosures = new ArrayList<>(List.of(row));

        CoefficientActivationResult result = new CoefficientActivationResult(touched, leavingClosures);
        touched.clear();
        leavingClosures.clear();

        assertThat(result.touched()).containsExactly(row);
        assertThat(result.leavingClosures()).containsExactly(row);
        assertThatThrownBy(() -> result.touched().add(row)).isInstanceOf(UnsupportedOperationException.class);
    }
}
