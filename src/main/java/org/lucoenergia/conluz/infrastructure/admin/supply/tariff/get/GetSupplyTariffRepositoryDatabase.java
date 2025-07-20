package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.get;

import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.admin.supply.tariff.GetSupplyTariffRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class GetSupplyTariffRepositoryDatabase implements GetSupplyTariffRepository {

    private final SupplyTariffRepository jpaRepository;
    private final SupplyTariffEntityMapper mapper;

    public GetSupplyTariffRepositoryDatabase(
            SupplyTariffRepository jpaRepository,
            SupplyTariffEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<SupplyTariff> findBySupplyId(SupplyId supplyId) {
        return jpaRepository.findBySupplyId(supplyId.getId())
                .map(mapper::map);
    }
}
