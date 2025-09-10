package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
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

                .withValidDateFrom(entity.getValidDateFrom())
                .withDistributor(entity.getDistributor())
                .withDistributorCode(entity.getDistributorCode())
                .withPointType(entity.getPointType())
                .withThirdParty(entity.getThirdParty())

                .withShellyMac(entity.getShellyMac())
                .withShellyId(entity.getShellyId())
                .withShellyMqttPrefix(entity.getShellyMqttPrefix())

                .build();
    }
}
