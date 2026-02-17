package org.lucoenergia.conluz.domain.admin.supply;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DatadisSupplyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializeWithAllKnownFields() throws JsonProcessingException {
        // Arrange
        String json = """
                {
                    "address": "Main Street 123",
                    "cups": "ES0123456789012345AB0F",
                    "postalCode": "28001",
                    "province": "Madrid",
                    "municipality": "Madrid",
                    "validDateFrom": "2020-01-01",
                    "validDateTo": "2025-12-31",
                    "pointType": 1,
                    "distributor": "Test Distributor",
                    "distributorCode": "123"
                }
                """;

        // Act
        DatadisSupply supply = objectMapper.readValue(json, DatadisSupply.class);

        // Assert
        Assertions.assertNotNull(supply, "DatadisSupply should not be null");
        Assertions.assertEquals("Main Street 123", supply.getAddress());
        Assertions.assertEquals("ES0123456789012345AB0F", supply.getCups());
        Assertions.assertEquals("28001", supply.getPostalCode());
        Assertions.assertEquals("Madrid", supply.getProvince());
        Assertions.assertEquals("Madrid", supply.getMunicipality());
        Assertions.assertEquals("2020-01-01", supply.getValidDateFrom());
        Assertions.assertEquals("2025-12-31", supply.getValidDateTo());
        Assertions.assertEquals(1, supply.getPointType());
        Assertions.assertEquals("Test Distributor", supply.getDistributor());
        Assertions.assertEquals("123", supply.getDistributorCode());
    }

    @Test
    void testDeserializeWithUnknownFields() throws JsonProcessingException {
        // Arrange - JSON with extra unknown fields including provinceCode
        String json = """
                {
                    "address": "Main Street 123",
                    "cups": "ES0123456789012345AB0F",
                    "postalCode": "28001",
                    "province": "Madrid",
                    "provinceCode": "28",
                    "municipality": "Madrid",
                    "validDateFrom": "2020-01-01",
                    "validDateTo": "2025-12-31",
                    "pointType": 1,
                    "distributor": "Test Distributor",
                    "distributorCode": "123",
                    "someUnknownField": "value",
                    "anotherUnknownField": 999
                }
                """;

        // Act
        DatadisSupply supply = objectMapper.readValue(json, DatadisSupply.class);

        // Assert - Unknown fields should be ignored
        Assertions.assertNotNull(supply, "DatadisSupply should not be null");
        Assertions.assertEquals("Main Street 123", supply.getAddress());
        Assertions.assertEquals("ES0123456789012345AB0F", supply.getCups());
        Assertions.assertEquals("28001", supply.getPostalCode());
        Assertions.assertEquals("Madrid", supply.getProvince());
        Assertions.assertEquals("Madrid", supply.getMunicipality());
        Assertions.assertEquals("2020-01-01", supply.getValidDateFrom());
        Assertions.assertEquals("2025-12-31", supply.getValidDateTo());
        Assertions.assertEquals(1, supply.getPointType());
        Assertions.assertEquals("Test Distributor", supply.getDistributor());
        Assertions.assertEquals("123", supply.getDistributorCode());
    }

    @Test
    void testDeserializeWithPartialFields() throws JsonProcessingException {
        // Arrange - JSON with only essential fields
        String json = """
                {
                    "cups": "ES0123456789012345AB0F",
                    "distributor": "Test Distributor"
                }
                """;

        // Act
        DatadisSupply supply = objectMapper.readValue(json, DatadisSupply.class);

        // Assert
        Assertions.assertNotNull(supply, "DatadisSupply should not be null");
        Assertions.assertEquals("ES0123456789012345AB0F", supply.getCups());
        Assertions.assertEquals("Test Distributor", supply.getDistributor());
        Assertions.assertNull(supply.getAddress());
        Assertions.assertNull(supply.getPostalCode());
        Assertions.assertNull(supply.getProvince());
        Assertions.assertNull(supply.getMunicipality());
    }

    @Test
    void testBuilderPattern() {
        // Arrange & Act
        DatadisSupply supply = new DatadisSupply.Builder()
                .withAddress("Main Street 123")
                .withCups("ES0123456789012345AB0F")
                .withPostalCode("28001")
                .withProvince("Madrid")
                .withMunicipality("Madrid")
                .withValidDateFrom("2020-01-01")
                .withValidDateTo("2025-12-31")
                .withPointType(1)
                .withDistributor("Test Distributor")
                .withDistributorCode("123")
                .build();

        // Assert
        Assertions.assertNotNull(supply, "DatadisSupply should not be null");
        Assertions.assertEquals("Main Street 123", supply.getAddress());
        Assertions.assertEquals("ES0123456789012345AB0F", supply.getCups());
        Assertions.assertEquals("28001", supply.getPostalCode());
        Assertions.assertEquals("Madrid", supply.getProvince());
        Assertions.assertEquals("Madrid", supply.getMunicipality());
        Assertions.assertEquals("2020-01-01", supply.getValidDateFrom());
        Assertions.assertEquals("2025-12-31", supply.getValidDateTo());
        Assertions.assertEquals(1, supply.getPointType());
        Assertions.assertEquals("Test Distributor", supply.getDistributor());
        Assertions.assertEquals("123", supply.getDistributorCode());
    }

    @Test
    void testDeserializeWithNullValues() throws JsonProcessingException {
        // Arrange - JSON with explicit null values
        String json = """
                {
                    "address": null,
                    "cups": "ES0123456789012345AB0F",
                    "postalCode": null,
                    "province": null,
                    "municipality": null,
                    "validDateFrom": null,
                    "validDateTo": null,
                    "pointType": null,
                    "distributor": "Test Distributor",
                    "distributorCode": null
                }
                """;

        // Act
        DatadisSupply supply = objectMapper.readValue(json, DatadisSupply.class);

        // Assert
        Assertions.assertNotNull(supply, "DatadisSupply should not be null");
        Assertions.assertNull(supply.getAddress());
        Assertions.assertEquals("ES0123456789012345AB0F", supply.getCups());
        Assertions.assertNull(supply.getPostalCode());
        Assertions.assertNull(supply.getProvince());
        Assertions.assertNull(supply.getMunicipality());
        Assertions.assertNull(supply.getValidDateFrom());
        Assertions.assertNull(supply.getValidDateTo());
        Assertions.assertNull(supply.getPointType());
        Assertions.assertEquals("Test Distributor", supply.getDistributor());
        Assertions.assertNull(supply.getDistributorCode());
    }
}
