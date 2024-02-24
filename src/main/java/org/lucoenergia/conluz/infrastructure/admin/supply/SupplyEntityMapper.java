package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.springframework.stereotype.Component;

@Component
public class SupplyEntityMapper extends BaseMapper<SupplyEntity, Supply> {

    @Override
    public Supply map(SupplyEntity entity) {
        return new Supply.Builder()
                .withId(entity.getId())
                .withAddress(entity.getAddress())
                .withPartitionCoefficient(entity.getPartitionCoefficient())
                .withEnabled(entity.getEnabled())
                .withName(entity.getName())
                .build();
    }
}
