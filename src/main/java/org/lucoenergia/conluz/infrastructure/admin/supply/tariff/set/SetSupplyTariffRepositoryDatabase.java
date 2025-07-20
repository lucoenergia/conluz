package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.set;

import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SetSupplyTariffRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class SetSupplyTariffRepositoryDatabase implements SetSupplyTariffRepository {

    private final org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffRepository jpaRepository;
    private final SupplyTariffEntityMapper mapper;

    public SetSupplyTariffRepositoryDatabase(
            org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffRepository jpaRepository,
            SupplyTariffEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public SupplyTariff save(SupplyTariff supplyTariff) {
        SupplyTariffEntity entity = mapper.mapToEntity(supplyTariff);
        SupplyTariffEntity savedEntity = jpaRepository.save(entity);
        return mapper.map(savedEntity);
    }
}