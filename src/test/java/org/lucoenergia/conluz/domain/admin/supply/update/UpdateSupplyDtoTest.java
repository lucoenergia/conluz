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
                .partitionCoefficient(0.75f)
                .build();

        Supply.Builder supplyBuilder = new Supply.Builder();

        // Act
        Supply supply = updateSupplyDto.mapToSupply(supplyBuilder);

        // Assert
        assertEquals("SUP123", supply.getCode());
        assertEquals("Supply Name", supply.getName());
        assertEquals("123 Main Street", supply.getAddress());
        assertEquals("Ref123", supply.getAddressRef());
        assertEquals(0.75f, supply.getPartitionCoefficient());
    }

    @Test
    void testMapToSupply_withNullPartitionCoefficient_setsNullInSupply() {
        // Arrange
        UpdateSupplyDto updateSupplyDto = new UpdateSupplyDto.Builder()
                .code("SUP124")
                .name("Supply Name 2")
                .address("456 Another Street")
                .addressRef("Ref456")
                .partitionCoefficient(null)
                .build();

        Supply.Builder supplyBuilder = new Supply.Builder();

        // Act
        Supply supply = updateSupplyDto.mapToSupply(supplyBuilder);

        // Assert
        assertEquals("SUP124", supply.getCode());
        assertEquals("Supply Name 2", supply.getName());
        assertEquals("456 Another Street", supply.getAddress());
        assertEquals("Ref456", supply.getAddressRef());
        assertEquals(null, supply.getPartitionCoefficient());
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
        assertEquals(null, supply.getAddress());
        assertEquals(null, supply.getAddressRef());
        assertEquals(null, supply.getPartitionCoefficient());
    }
}