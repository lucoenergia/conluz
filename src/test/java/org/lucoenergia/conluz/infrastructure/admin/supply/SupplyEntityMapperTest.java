package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;

class SupplyEntityMapperTest {

    private final SupplyEntityMapper mapper = new SupplyEntityMapper();

    @Test
    void testMap() {
        SupplyEntity entity = SupplyEntityMother.random();

        Supply result = mapper.map(entity);

        Assertions.assertEquals(entity.getCode(), result.getCode());
        Assertions.assertEquals(entity.getName(), result.getName());
        Assertions.assertEquals(entity.getAddress(), result.getAddress());
        Assertions.assertEquals(entity.getPartitionCoefficient(), result.getPartitionCoefficient());
        Assertions.assertEquals(entity.getEnabled(), result.getEnabled());
    }
}
