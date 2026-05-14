package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.infrastructure.admin.supply.contract.SupplyContractEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.datadis.SupplyDatadisEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.distributor.SupplyDistributorEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.shelly.SupplyShellyEntity;
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

        SupplyContractEntity contract = entity.getContract();
        SupplyDistributorEntity distributor = entity.getDistributor();
        SupplyDatadisEntity datadis = entity.getDatadis();
        SupplyShellyEntity shelly = entity.getShelly();

        Assertions.assertEquals(contract.getValidDateFrom(), result.getContract().getValidDateFrom());
        Assertions.assertEquals(distributor.getDistributor(), result.getDistributor().getName());
        Assertions.assertEquals(distributor.getDistributorCode(), result.getDistributor().getCode());
        Assertions.assertEquals(distributor.getPointType(), result.getDistributor().getPointType());
        Assertions.assertEquals(datadis.getThirdParty(), result.getDatadis().isThirdParty());

        Assertions.assertEquals(shelly.getShellyMac(), result.getShelly().getMac());
        Assertions.assertEquals(shelly.getShellyId(), result.getShelly().getId());
        Assertions.assertEquals(shelly.getShellyMqttPrefix(), result.getShelly().getMqttPrefix());
    }
}
