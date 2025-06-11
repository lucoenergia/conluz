package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateSupplyBodyTest {

    @Test
    void testMapToSupply() {
        // Arrange
        UUID supplyId = UUID.randomUUID();
        UpdateSupplyBody updateSupplyBody = new UpdateSupplyBody();
        updateSupplyBody.setCode("code");
        updateSupplyBody.setName("name");
        updateSupplyBody.setAddress("address");
        updateSupplyBody.setPartitionCoefficient(0.5f);
        updateSupplyBody.setDatadisValidDateFrom("2022-01-01");
        updateSupplyBody.setDatadisDistributor("distributor");
        updateSupplyBody.setDatadisDistributorCode("distributorCode");
        updateSupplyBody.setDatadisPointType(6);
        updateSupplyBody.setShellyMac("shellyMac");
        updateSupplyBody.setShellyId("shellyId");
        updateSupplyBody.setShellyMqttPrefix("shellyMqttPrefix");

        // Act
        Supply supply = updateSupplyBody.mapToSupply(supplyId);

        // Assert
        assertEquals(supplyId, supply.getId());
        assertEquals("code", supply.getCode());
        assertEquals("name", supply.getName());
        assertEquals("address", supply.getAddress());
        assertEquals(0.5f, supply.getPartitionCoefficient());
        assertEquals("2022-01-01", supply.getValidDateFrom().toString());
        assertEquals("distributor", supply.getDistributor());
        assertEquals("distributorCode", supply.getDistributorCode());
        assertEquals(6, supply.getPointType());
        assertEquals("shellyMac", supply.getShellyMac());
        assertEquals("shellyId", supply.getShellyId());
        assertEquals("shellyMqttPrefix", supply.getShellyMqttPrefix());
    }
}