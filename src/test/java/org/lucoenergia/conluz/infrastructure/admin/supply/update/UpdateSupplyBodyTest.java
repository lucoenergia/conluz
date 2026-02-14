package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyDto;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateSupplyBodyTest {

    @Test
    void testMapToSupply() {
        // Arrange
        UpdateSupplyBody updateSupplyBody = new UpdateSupplyBody();
        updateSupplyBody.setCode("code");
        updateSupplyBody.setName("name");
        updateSupplyBody.setAddress("address");
        updateSupplyBody.setAddressRef("addressRef");
        updateSupplyBody.setPartitionCoefficient(0.5f);

        // Act
        UpdateSupplyDto supply = updateSupplyBody.mapToSupply();

        // Assert
        assertEquals("code", supply.getCode());
        assertEquals("name", supply.getName());
        assertEquals("address", supply.getAddress());
        assertEquals("addressRef", supply.getAddressRef());
        assertEquals(0.5f, supply.getPartitionCoefficient());
    }
}