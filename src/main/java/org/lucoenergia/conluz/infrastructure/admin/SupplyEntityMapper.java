package org.lucoenergia.conluz.infrastructure.admin;

import org.lucoenergia.conluz.domain.admin.Supply;
import org.springframework.stereotype.Component;

@Component
public class SupplyEntityMapper {

    public Supply map(SupplyEntity entity) {
        return new Supply(entity.getId(), entity.getName(), entity.getAddress(), entity.getPartitionCoefficient(),
                entity.getEnabled());
    }
}
