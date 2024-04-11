package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Component
public class PlantEntityMapper extends BaseMapper<PlantEntity, Plant> {

    private final UserEntityMapper userEntityMapper;

    public PlantEntityMapper(UserEntityMapper userEntityMapper) {
        this.userEntityMapper = userEntityMapper;
    }

    @Override
    public Plant map(PlantEntity entity) {
        return new Plant.Builder()
                .withId(entity.getId())
                .withCode(entity.getCode())
                .withAddress(entity.getAddress())
                .withName(entity.getName())
                .withUser(userEntityMapper.map(entity.getUser()))
                .withDescription(entity.getDescription())
                .withTotalPower(entity.getTotalPower())
                .withConnectionDate(entity.getConnectionDate())
                .withInverterProvider(entity.getInverterProvider())
                .build();
    }
}
