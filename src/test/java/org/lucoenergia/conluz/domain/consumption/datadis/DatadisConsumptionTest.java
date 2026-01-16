package org.lucoenergia.conluz.domain.consumption.datadis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DatadisConsumptionTest {

    /**
     * Tests for the {@link DatadisConsumption#isEmpty()} method.
     * The isEmpty method determines if all energy-related fields (consumptionKWh, surplusEnergyKWh,
     * generationEnergyKWh, and selfConsumptionEnergyKWh) are either null or zero.
     */

    @Test
    void testIsEmpty_AllFieldsNull_ReturnsTrue() {
        // Arrange
        DatadisConsumption datadisConsumption = new DatadisConsumption();

        // Act
        boolean result = datadisConsumption.isEmpty();

        // Assert
        Assertions.assertTrue(result, "isEmpty should return true when all fields are null.");
    }

    @Test
    void testIsEmpty_AllFieldsZero_ReturnsTrue() {
        // Arrange
        DatadisConsumption datadisConsumption = new DatadisConsumption();
        datadisConsumption.setConsumptionKWh(0f);
        datadisConsumption.setSurplusEnergyKWh(0f);
        datadisConsumption.setGenerationEnergyKWh(0f);
        datadisConsumption.setSelfConsumptionEnergyKWh(0f);

        // Act
        boolean result = datadisConsumption.isEmpty();

        // Assert
        Assertions.assertTrue(result, "isEmpty should return true when all fields are zero.");
    }

    @Test
    void testIsEmpty_OneFieldNonZero_ReturnsFalse() {
        // Arrange
        DatadisConsumption datadisConsumption = new DatadisConsumption();
        datadisConsumption.setConsumptionKWh(5f);

        // Act
        boolean result = datadisConsumption.isEmpty();

        // Assert
        Assertions.assertFalse(result, "isEmpty should return false when one field is non-zero.");
    }

    @Test
    void testIsEmpty_MultipleFieldsNonZero_ReturnsFalse() {
        // Arrange
        DatadisConsumption datadisConsumption = new DatadisConsumption();
        datadisConsumption.setConsumptionKWh(5f);
        datadisConsumption.setSurplusEnergyKWh(3f);

        // Act
        boolean result = datadisConsumption.isEmpty();

        // Assert
        Assertions.assertFalse(result, "isEmpty should return false when multiple fields are non-zero.");
    }

    @Test
    void testIsEmpty_FieldCombinationWithNullAndZero_ReturnsTrue() {
        // Arrange
        DatadisConsumption datadisConsumption = new DatadisConsumption();
        datadisConsumption.setConsumptionKWh(0f);
        datadisConsumption.setSurplusEnergyKWh(null);
        datadisConsumption.setGenerationEnergyKWh(0f);
        datadisConsumption.setSelfConsumptionEnergyKWh(null);

        // Act
        boolean result = datadisConsumption.isEmpty();

        // Assert
        Assertions.assertTrue(result, "isEmpty should return true when all fields are either null or zero.");
    }
}