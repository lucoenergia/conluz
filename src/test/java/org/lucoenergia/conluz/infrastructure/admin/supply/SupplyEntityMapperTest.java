package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;

class SupplyEntityMapperTest {

    private final UserEntityMapper userEntityMapper = new UserEntityMapper();
    private final SupplyEntityMapper mapper = new SupplyEntityMapper(userEntityMapper);

    @Test
    void testMap() {
        SupplyEntity entity = SupplyEntityMother.random();

        Supply result = mapper.map(entity);

        Assertions.assertEquals(entity.getCode(), result.getCode());
        Assertions.assertEquals(entity.getName(), result.getName());
        Assertions.assertEquals(entity.getAddress(), result.getAddress());
        Assertions.assertEquals(entity.getPartitionCoefficient(), result.getPartitionCoefficient());
        Assertions.assertEquals(entity.getEnabled(), result.getEnabled());
        Assertions.assertEquals(entity.getUser().getPersonalId(), result.getUser().getPersonalId());

        Assertions.assertEquals(entity.getDatadisValidDateFrom(), result.getDatadisValidDateFrom());
        Assertions.assertEquals(entity.getDatadisDistributor(), result.getDatadisDistributor());
        Assertions.assertEquals(entity.getDatadisDistributorCode(), result.getDatadisDistributorCode());
        Assertions.assertEquals(entity.getDatadisPointType(), result.getDatadisPointType());

        Assertions.assertEquals(entity.getShellyMac(), result.getShellyMac());
        Assertions.assertEquals(entity.getShellyId(), result.getShellyId());
        Assertions.assertEquals(entity.getShellyMqttPrefix(), result.getShellyMqttPrefix());
    }
}
