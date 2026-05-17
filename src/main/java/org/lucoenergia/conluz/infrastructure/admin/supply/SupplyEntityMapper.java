package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.datadis.SupplyDatadis;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.shelly.SupplyShelly;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.contract.SupplyContractEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.datadis.SupplyDatadisEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.distributor.SupplyDistributorEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.shelly.SupplyShellyEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Component
public class SupplyEntityMapper extends BaseMapper<SupplyEntity, Supply> {

    private final UserEntityMapper userEntityMapper;

    public SupplyEntityMapper(UserEntityMapper userEntityMapper) {
        this.userEntityMapper = userEntityMapper;
    }

    @Override
    public Supply map(SupplyEntity entity) {
        return new Supply.Builder()
                .withId(entity.getId())
                .withCode(entity.getCode())
                .withName(entity.getName())
                .withAddress(entity.getAddress())
                .withAddressRef(entity.getAddressRef())
                .withPartitionCoefficient(entity.getPartitionCoefficient())
                .withEnabled(entity.getEnabled())
                .withUser(userEntityMapper.map(entity.getUser()))

                .withShelly(mapShelly(entity.getShelly()))
                .withDatadis(mapDatadis(entity.getDatadis()))
                .withDistributor(mapDistributor(entity.getDistributor()))
                .withContract(mapContract(entity.getContract()))

                .build();
    }

    private SupplyShelly mapShelly(SupplyShellyEntity entity) {
        if (entity == null) {
            return null;
        }
        return new SupplyShelly.Builder()
                .withMacAddress(entity.getMacAddress())
                .withId(entity.getId())
                .withMqttPrefix(entity.getMqttPrefix())
                .build();
    }

    private SupplyDatadis mapDatadis(SupplyDatadisEntity entity) {
        if (entity == null) {
            return null;
        }
        return new SupplyDatadis.Builder()
                .withThirdParty(entity.getThirdParty())
                .build();
    }

    private SupplyDistributor mapDistributor(SupplyDistributorEntity entity) {
        if (entity == null) {
            return null;
        }
        return new SupplyDistributor.Builder()
                .withName(entity.getName())
                .withCode(entity.getCode())
                .withPointType(entity.getPointType())
                .build();
    }

    private SupplyContract mapContract(SupplyContractEntity entity) {
        if (entity == null) {
            return null;
        }
        return new SupplyContract.Builder()
                .withValidDateFrom(entity.getValidDateFrom())
                .build();
    }
}
