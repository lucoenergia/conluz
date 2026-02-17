package org.lucoenergia.conluz.domain.consumption.datadis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DatadisConsumptionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    /**
     * Test for JSON deserialization with unknown fields.
     * This test verifies that the @JsonIgnoreProperties annotation works correctly
     * and unknown fields from Datadis API are silently ignored.
     */
    @Test
    void testDeserializeWithUnknownFields() throws JsonProcessingException {
        // Arrange - JSON with extra unknown fields
        String json = """
                {
                    "cups": "ES0123456789012345AB0F",
                    "date": "2023-01-15",
                    "time": "12:00",
                    "consumptionKWh": 2.5,
                    "obtainMethod": "Real",
                    "surplusEnergyKWh": 0.5,
                    "generationEnergyKWh": 3.0,
                    "selfConsumptionEnergyKWh": 2.0,
                    "unknownField1": "someValue",
                    "unknownField2": 123,
                    "newApiField": true
                }
                """;

        // Act
        DatadisConsumption consumption = objectMapper.readValue(json, DatadisConsumption.class);

        // Assert - Unknown fields should be ignored, known fields should be set
        Assertions.assertNotNull(consumption, "DatadisConsumption should not be null");
        Assertions.assertEquals("ES0123456789012345AB0F", consumption.getCups());
        Assertions.assertEquals("2023-01-15", consumption.getDate());
        Assertions.assertEquals("12:00", consumption.getTime());
        Assertions.assertEquals(2.5f, consumption.getConsumptionKWh());
        Assertions.assertEquals("Real", consumption.getObtainMethod());
        Assertions.assertEquals(0.5f, consumption.getSurplusEnergyKWh());
        Assertions.assertEquals(3.0f, consumption.getGenerationEnergyKWh());
        Assertions.assertEquals(2.0f, consumption.getSelfConsumptionEnergyKWh());
    }
}