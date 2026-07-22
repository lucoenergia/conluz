package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test suite for the {@link PartitionCoefficientSetSumWarning} class.
 * This class specifically tests the static {@code build} method,
 * which is responsible for generating a warning message if the sum of coefficients deviates
 * from the expected value of 1 by more than the defined tolerance.
 */
class PartitionCoefficientSetSumWarningTest {

    @Test
    void testBuildReturnsNullWhenSumIsNull() {
        // Test scenario: If the input sum is null, the method should return null.
        BigDecimal inputSum = null;

        String result = PartitionCoefficientSetSumWarning.build(inputSum);

        assertNull(result, "Expected result to be null when sum is null.");
    }

    @Test
    void testBuildReturnsNullWhenSumIsWithinTolerance() {
        // Test scenario: If the sum is within the acceptable tolerance, the method should return null.
        BigDecimal inputSum = BigDecimal.valueOf(1.00005);

        String result = PartitionCoefficientSetSumWarning.build(inputSum);

        assertNull(result, "Expected result to be null when sum is within tolerance.");
    }

    @Test
    void testBuildReturnsWarningMessageWhenSumExceedsToleranceAbove() {
        // Test scenario: If the sum exceeds the expected value plus tolerance, the method should return a warning message.
        BigDecimal inputSum = BigDecimal.valueOf(1.0002);

        String result = PartitionCoefficientSetSumWarning.build(inputSum);

        assertEquals("Coefficient set sum is 1.0002, expected 1", result,
                "Expected warning message for sum exceeding tolerance above the expected value.");
    }

    @Test
    void testBuildReturnsWarningMessageWhenSumExceedsToleranceBelow() {
        // Test scenario: If the sum is below the expected value minus tolerance, the method should return a warning message.
        BigDecimal inputSum = BigDecimal.valueOf(0.9998);

        String result = PartitionCoefficientSetSumWarning.build(inputSum);

        assertEquals("Coefficient set sum is 0.9998, expected 1", result,
                "Expected warning message for sum exceeding tolerance below the expected value.");
    }

    @Test
    void testBuildHandlesZeroValue() {
        // Test scenario: If the sum equals zero, the method should return a warning message.
        BigDecimal inputSum = BigDecimal.ZERO;

        String result = PartitionCoefficientSetSumWarning.build(inputSum);

        assertEquals("Coefficient set sum is 0, expected 1", result,
                "Expected warning message for sum equal to zero.");
    }

    @Test
    void testBuildHandlesLargeDeviation() {
        // Test scenario: If the sum significantly deviates from 1, the method should return an appropriate warning message.
        BigDecimal inputSum = BigDecimal.valueOf(1.5);

        String result = PartitionCoefficientSetSumWarning.build(inputSum);

        assertEquals("Coefficient set sum is 1.5, expected 1", result,
                "Expected warning message for large deviation of the sum from the expected value.");
    }

    @Test
    void testBuildHandlesExactExpectedValue() {
        // Test scenario: If the sum is exactly equal to 1 (the expected value), the method should return null.
        BigDecimal inputSum = BigDecimal.ONE;

        String result = PartitionCoefficientSetSumWarning.build(inputSum);

        assertNull(result, "Expected result to be null when sum equals the expected value.");
    }

    @Test
    void testBuildHandlesTrailingZerosInSum() {
        // Test scenario: The method should correctly handle sums with trailing zeros and strip them in the warning message.
        BigDecimal inputSum = new BigDecimal("1.00020000");

        String result = PartitionCoefficientSetSumWarning.build(inputSum);

        assertEquals("Coefficient set sum is 1.0002, expected 1", result,
                "Expected warning message to correctly handle and strip trailing zeros.");
    }
}