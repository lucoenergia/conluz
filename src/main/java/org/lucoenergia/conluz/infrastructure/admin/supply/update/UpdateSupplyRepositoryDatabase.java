package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class UpdateSupplyRepositoryDatabase implements UpdateSupplyRepository {

    private final SupplyRepository repository;
    private final SupplyEntityMapper mapper;

    public UpdateSupplyRepositoryDatabase(SupplyRepository repository, SupplyEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Supply update(Supply supply) {
        UUID supplyUuid = supply.getId();
        Optional<SupplyEntity> result = repository.findById(supplyUuid);
        if (result.isEmpty()) {
            throw new SupplyNotFoundException(SupplyId.of(supplyUuid));
        }
        SupplyEntity currentSupply = result.get();
        currentSupply.setCode(supply.getCode());
        currentSupply.setName(supply.getName());
        currentSupply.setAddress(supply.getAddress());
        currentSupply.setPartitionCoefficient(supply.getPartitionCoefficient());

        currentSupply.setDatadisValidDateFrom(supply.getDatadisValidDateFrom());
        currentSupply.setDatadisDistributor(supply.getDatadisDistributor());
        currentSupply.setDatadisDistributorCode(supply.getDatadisDistributorCode());
        currentSupply.setDatadisPointType(supply.getDatadisPointType());

        currentSupply.setShellyMac(supply.getShellyMac());
        currentSupply.setShellyId(supply.getShellyId());
        currentSupply.setShellyMqttPrefix(supply.getShellyMqttPrefix());

        return mapper.map(repository.save(currentSupply));
    }
}
