package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.springframework.stereotype.Component;

@Component
public class SupplyEntityMapper extends BaseMapper<SupplyEntity, Supply> {

    @Override
    public Supply map(SupplyEntity entity) {
        return new Supply(entity.getId(), entity.getName(), entity.getAddress(), entity.getPartitionCoefficient(),
                entity.getEnabled());
    }
}
