package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient.PartitionCoefficientResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplacePartitionCoefficientsResponseTest {

    /**
     * Test: Verifies that the getCoefficients method returns the correct list of PartitionCoefficientResponse objects.
     */
    @Test
    void testGetCoefficients_ReturnsCorrectResponses() {
        // Arrange
        SupplyPartitionCoefficient coefficient1 = new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withCoefficient(BigDecimal.valueOf(0.5))
                .withCreatedAt(Instant.now())
                .build();

        SupplyPartitionCoefficient coefficient2 = new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withCoefficient(BigDecimal.valueOf(0.5))
                .withCreatedAt(Instant.now())
                .build();

        List<SupplyPartitionCoefficient> coefficients = List.of(coefficient1, coefficient2);

        // Act
        ReplacePartitionCoefficientsResponse response = new ReplacePartitionCoefficientsResponse(coefficients);
        List<PartitionCoefficientResponse> result = response.getCoefficients();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(coefficient1.getId(), result.get(0).getId());
        assertEquals(coefficient2.getId(), result.get(1).getId());
    }

    /**
     * Test: Verifies that getCoefficientSumWarning method returns null when the sum of coefficients is exactly 1.
     */
    @Test
    void testGetCoefficientSumWarning_ReturnsNullWhenSumIsOne() {
        // Arrange
        SupplyPartitionCoefficient coefficient1 = new SupplyPartitionCoefficient.Builder()
                .withCoefficient(BigDecimal.valueOf(0.5))
                .withCreatedAt(Instant.now())
                .build();

        SupplyPartitionCoefficient coefficient2 = new SupplyPartitionCoefficient.Builder()
                .withCoefficient(BigDecimal.valueOf(0.5))
                .withCreatedAt(Instant.now())
                .build();

        List<SupplyPartitionCoefficient> coefficients = List.of(coefficient1, coefficient2);

        // Act
        ReplacePartitionCoefficientsResponse response = new ReplacePartitionCoefficientsResponse(coefficients);
        String warning = response.getCoefficientSumWarning();

        // Assert
        assertNull(warning);
    }

    /**
     * Test: Verifies that the getCoefficientSumWarning method returns a warning message when the sum of coefficients deviates from 1.
     */
    @Test
    void testGetCoefficientSumWarning_ReturnsWarningMessageWhenSumDeviatesFromOne() {
        // Arrange
        SupplyPartitionCoefficient coefficient1 = new SupplyPartitionCoefficient.Builder()
                .withCoefficient(BigDecimal.valueOf(0.3))
                .withCreatedAt(Instant.now())
                .build();

        SupplyPartitionCoefficient coefficient2 = new SupplyPartitionCoefficient.Builder()
                .withCoefficient(BigDecimal.valueOf(0.6))
                .withCreatedAt(Instant.now())
                .build();

        List<SupplyPartitionCoefficient> coefficients = List.of(coefficient1, coefficient2);

        // Act
        ReplacePartitionCoefficientsResponse response = new ReplacePartitionCoefficientsResponse(coefficients);
        String warning = response.getCoefficientSumWarning();

        // Assert
        assertNotNull(warning);
        assertTrue(warning.contains("0.9"));
    }

    /**
     * Test: Verifies the handling of an empty coefficient list.
     */
    @Test
    void testWithEmptyCoefficientList() {
        // Arrange
        List<SupplyPartitionCoefficient> coefficients = List.of();

        // Act
        ReplacePartitionCoefficientsResponse response = new ReplacePartitionCoefficientsResponse(coefficients);

        // Assert
        assertTrue(response.getCoefficients().isEmpty());
        assertNotNull(response.getCoefficientSumWarning());
        assertTrue(response.getCoefficientSumWarning().contains("Coefficient set sum is 0, expected 1"));
    }
}