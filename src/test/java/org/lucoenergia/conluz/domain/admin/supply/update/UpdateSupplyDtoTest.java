package org.lucoenergia.conluz.domain.admin.supply.update;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateSupplyDtoTest {

    @Test
    void testMapToSupply_withAllFieldsSet_correctlyMapsFields() {
        // Arrange
        UpdateSupplyDto updateSupplyDto = new UpdateSupplyDto.Builder()
                .code("SUP123")
                .name("Supply Name")
                .address("123 Main Street")
                .addressRef("Ref123")
                .build();

        Supply.Builder supplyBuilder = new Supply.Builder();

        // Act
        Supply supply = updateSupplyDto.mapToSupply(supplyBuilder);

        // Assert
        assertEquals("SUP123", supply.getCode());
        assertEquals("Supply Name", supply.getName());
        assertEquals("123 Main Street", supply.getAddress());
        assertEquals("Ref123", supply.getAddressRef());
    }

    @Test
    void testMapToSupply_withNullName_preservesExistingName() {
        // Arrange
        UpdateSupplyDto updateSupplyDto = new UpdateSupplyDto.Builder()
                .code("SUP126")
                .address("789 Test Street")
                .addressRef("Ref789")
                .build();

        Supply.Builder supplyBuilder = new Supply.Builder().withName("Existing Name");

        // Act
        Supply supply = updateSupplyDto.mapToSupply(supplyBuilder);

        // Assert
        assertEquals("Existing Name", supply.getName());
    }

    @Test
    void testMapToSupply_withMinimalFieldsSet_correctlyMapsFields() {
        // Arrange
        UpdateSupplyDto updateSupplyDto = new UpdateSupplyDto.Builder()
                .code("SUP125")
                .name("Minimal Supply")
                .build();

        Supply.Builder supplyBuilder = new Supply.Builder();

        // Act
        Supply supply = updateSupplyDto.mapToSupply(supplyBuilder);

        // Assert
        assertEquals("SUP125", supply.getCode());
        assertEquals("Minimal Supply", supply.getName());
        assertEquals(0F, supply.getPartitionCoefficient());
        assertEquals(null, supply.getAddress());
        assertEquals(null, supply.getAddressRef());
    }
}