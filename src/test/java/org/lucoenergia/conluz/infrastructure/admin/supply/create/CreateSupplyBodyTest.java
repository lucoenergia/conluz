package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;

import static org.junit.jupiter.api.Assertions.*;

class CreateSupplyBodyTest {

    @Test
    void mapToSupply_ShouldMapAllFieldsCorrectly() {
        // Initialize input
        CreateSupplyBody body = new CreateSupplyBody();
        body.setCode(" SUP123  ");
        body.setPersonalId(" PERSONAL456  ");
        body.setAddress(" Test Address  ");
        body.setAddressRef("ASDFKSDF98I78");
        body.setName(" Supply Name ");

        // Perform mapping
        Supply result = body.mapToSupply();

        // Assertions
        assertNotNull(result);
        assertEquals("SUP123", result.getCode());
        assertEquals("PERSONAL456", result.getUser().getPersonalId());
        assertEquals("Test Address", result.getAddress());
        assertEquals(body.getAddressRef(), result.getAddressRef());
        assertEquals(0.0F, result.getPartitionCoefficient());
        assertEquals("Supply Name", result.getName());
    }

    @Test
    void mapToSupply_ShouldOmitName_WhenNameIsNull() {
        // Initialize input
        CreateSupplyBody body = new CreateSupplyBody();
        body.setCode("SUP123");
        body.setPersonalId("PERSONAL456");
        body.setAddress("Test Address");
        body.setAddressRef("ASDFKSDF98I78");
        body.setName(null);

        // Perform mapping
        Supply result = body.mapToSupply();

        // Assertions
        assertNotNull(result);
        assertEquals("SUP123", result.getCode());
        assertEquals("PERSONAL456", result.getUser().getPersonalId());
        assertEquals("Test Address", result.getAddress());
        assertEquals(body.getAddressRef(), result.getAddressRef());
        assertEquals(body.getAddressRef(), result.getAddressRef());
        assertEquals(0.0F, result.getPartitionCoefficient());
        assertNull(result.getName());
    }

    @Test
    void mapToSupply_ShouldOmitName_WhenNameIsBlank() {
        // Initialize input
        CreateSupplyBody body = new CreateSupplyBody();
        body.setCode("SUP123");
        body.setPersonalId("PERSONAL456");
        body.setAddress("Test Address");
        body.setAddressRef("ASDFKSDF98I78");
        body.setName("  ");

        // Perform mapping
        Supply result = body.mapToSupply();

        // Assertions
        assertNotNull(result);
        assertEquals("SUP123", result.getCode());
        assertEquals("PERSONAL456", result.getUser().getPersonalId());
        assertEquals("Test Address", result.getAddress());
        assertEquals(body.getAddressRef(), result.getAddressRef());
        assertEquals(0.0F, result.getPartitionCoefficient());
        assertNull(result.getName());
    }
}