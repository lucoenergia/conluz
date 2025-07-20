package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mapper between SupplyTariff domain model and SupplyTariffEntity
 */
@Transactional(readOnly = true)
@Component
public class SupplyTariffEntityMapper extends BaseMapper<SupplyTariffEntity, SupplyTariff> {

    private final SupplyEntityMapper supplyEntityMapper;

    public SupplyTariffEntityMapper(SupplyEntityMapper supplyEntityMapper) {
        this.supplyEntityMapper = supplyEntityMapper;
    }

    @Override
    public SupplyTariff map(SupplyTariffEntity entity) {
        return new SupplyTariff.Builder()
                .withId(entity.getId())
                .withSupply(supplyEntityMapper.map(entity.getSupply()))
                .withValley(entity.getValley())
                .withPeak(entity.getPeak())
                .withOffPeak(entity.getOffPeak())
                .build();
    }

    /**
     * Maps a SupplyTariff domain model to a SupplyTariffEntity
     * 
     * @param domainModel the domain model to map
     * @return the entity
     */
    public SupplyTariffEntity mapToEntity(SupplyTariff domainModel) {
        return new SupplyTariffEntity.Builder()
                .withId(domainModel.getId())
                .withSupply(domainModel.getSupply() != null ? 
                        new SupplyEntity.Builder().withId(domainModel.getSupply().getId()).build() :
                        null)
                .withValley(domainModel.getValley())
                .withPeak(domainModel.getPeak())
                .withOffPeak(domainModel.getOffPeak())
                .build();
    }
}
