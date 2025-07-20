package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SupplyTariffEntityMapperTest {

    private final SupplyEntityMapper supplyEntityMapper = Mockito.mock(SupplyEntityMapper.class);
    private final SupplyTariffEntityMapper mapper = new SupplyTariffEntityMapper(supplyEntityMapper);

    @Test
    void testMapToEntity_validSupplyTariff_shouldReturnCorrectSupplyTariffEntity() {
        // Given
        UUID supplyId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        Supply supply = new Supply.Builder().withId(supplyId).build();
        SupplyTariff supplyTariff = new SupplyTariff.Builder()
                .withId(tariffId)
                .withSupply(supply)
                .withValley(10.5)
                .withPeak(20.5)
                .withOffPeak(15.5)
                .build();

        // When
        SupplyTariffEntity supplyTariffEntity = mapper.mapToEntity(supplyTariff);

        // Then
        assertNotNull(supplyTariffEntity);
        assertEquals(tariffId, supplyTariffEntity.getId());
        assertNotNull(supplyTariffEntity.getSupply());
        assertEquals(supplyId, supplyTariffEntity.getSupply().getId());
        assertEquals(10.5, supplyTariffEntity.getValley());
        assertEquals(20.5, supplyTariffEntity.getPeak());
        assertEquals(15.5, supplyTariffEntity.getOffPeak());
    }

    @Test
    void testMapToEntity_supplyTariffWithZeroValues_shouldReturnValidSupplyTariffEntity() {
        // Given
        UUID supplyId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        Supply supply = new Supply.Builder().withId(supplyId).build();
        SupplyTariff supplyTariff = new SupplyTariff.Builder()
                .withId(tariffId)
                .withSupply(supply)
                .withValley(0.0)
                .withPeak(0.0)
                .withOffPeak(0.0)
                .build();

        // When
        SupplyTariffEntity supplyTariffEntity = mapper.mapToEntity(supplyTariff);

        // Then
        assertNotNull(supplyTariffEntity);
        assertEquals(tariffId, supplyTariffEntity.getId());
        assertNotNull(supplyTariffEntity.getSupply());
        assertEquals(supplyId, supplyTariffEntity.getSupply().getId());
        assertEquals(0.0, supplyTariffEntity.getValley());
        assertEquals(0.0, supplyTariffEntity.getPeak());
        assertEquals(0.0, supplyTariffEntity.getOffPeak());
    }

    @Test
    void testMap_validSupplyTariffEntity_shouldReturnCorrectSupplyTariff() {
        // Given
        UUID supplyId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        SupplyEntity supplyEntity = new SupplyEntity.Builder().withId(supplyId).build();
        SupplyTariffEntity supplyTariffEntity = new SupplyTariffEntity.Builder()
                .withId(tariffId)
                .withSupply(supplyEntity)
                .withValley(10.5)
                .withPeak(20.5)
                .withOffPeak(15.5)
                .build();

        Mockito.when(supplyEntityMapper.map(supplyEntity)).thenReturn(new Supply.Builder().withId(supplyId).build());


        // When
        SupplyTariff supplyTariff = mapper.map(supplyTariffEntity);

        // Then
        assertNotNull(supplyTariff);
        assertEquals(tariffId, supplyTariff.getId());
        assertNotNull(supplyTariff.getSupply());
        assertEquals(supplyId, supplyTariff.getSupply().getId());
        assertEquals(10.5, supplyTariff.getValley());
        assertEquals(20.5, supplyTariff.getPeak());
        assertEquals(15.5, supplyTariff.getOffPeak());
    }

    @Test
    void testMap_supplyTariffEntityWithZeroValues_shouldReturnValidSupplyTariff() {
        // Given
        UUID supplyId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        SupplyEntity supplyEntity = new SupplyEntity.Builder().withId(supplyId).build();
        SupplyTariffEntity supplyTariffEntity = new SupplyTariffEntity.Builder()
                .withId(tariffId)
                .withSupply(supplyEntity)
                .withValley(0.0)
                .withPeak(0.0)
                .withOffPeak(0.0)
                .build();
        Mockito.when(supplyEntityMapper.map(supplyEntity)).thenReturn(new Supply.Builder().withId(supplyId).build());

        // When
        SupplyTariff supplyTariff = mapper.map(supplyTariffEntity);

        // Then
        assertNotNull(supplyTariff);
        assertEquals(tariffId, supplyTariff.getId());
        assertNotNull(supplyTariff.getSupply());
        assertEquals(supplyId, supplyTariff.getSupply().getId());
        assertEquals(0.0, supplyTariff.getValley());
        assertEquals(0.0, supplyTariff.getPeak());
        assertEquals(0.0, supplyTariff.getOffPeak());
    }
}