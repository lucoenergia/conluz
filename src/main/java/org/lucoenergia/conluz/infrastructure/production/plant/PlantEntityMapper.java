package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Component
public class PlantEntityMapper extends BaseMapper<PlantEntity, Plant> {

    private final SupplyEntityMapper supplyEntityMapper;

    public PlantEntityMapper(SupplyEntityMapper supplyEntityMapper) {
        this.supplyEntityMapper = supplyEntityMapper;
    }

    @Override
    public Plant map(PlantEntity entity) {
        return new Plant.Builder()
                .withId(entity.getId())
                .withCode(entity.getCode())
                .withAddress(entity.getAddress())
                .withName(entity.getName())
                .withSupply(supplyEntityMapper.map(entity.getSupply()))
                .withDescription(entity.getDescription())
                .withTotalPower(entity.getTotalPower())
                .withConnectionDate(entity.getConnectionDate())
                .withInverterProvider(entity.getInverterProvider())
                .build();
    }
}
