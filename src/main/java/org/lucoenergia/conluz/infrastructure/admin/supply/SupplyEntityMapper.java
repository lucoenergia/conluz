package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.springframework.stereotype.Component;

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
                .withAddress(entity.getAddress())
                .withPartitionCoefficient(entity.getPartitionCoefficient())
                .withEnabled(entity.getEnabled())
                .withName(entity.getName())
                .withUser(userEntityMapper.map(entity.getUser()))
                .build();
    }
}
