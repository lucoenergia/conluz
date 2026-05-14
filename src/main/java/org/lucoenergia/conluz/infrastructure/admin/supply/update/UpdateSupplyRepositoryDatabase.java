package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.shelly.SupplyShellyEntity;
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
        currentSupply.setAddressRef(supply.getAddressRef());
        currentSupply.setPartitionCoefficient(supply.getPartitionCoefficient());

        if (supply.getContract() != null) {
            if (currentSupply.getContract() == null) {
                currentSupply.setContract(new org.lucoenergia.conluz.infrastructure.admin.supply.contract.SupplyContractEntity());
            }
            currentSupply.getContract().setValidDateFrom(supply.getContract().getValidDateFrom());
        }

        if (supply.getDistributor() != null) {
            if (currentSupply.getDistributor() == null) {
                currentSupply.setDistributor(new org.lucoenergia.conluz.infrastructure.admin.supply.distributor.SupplyDistributorEntity());
            }
            currentSupply.getDistributor().setDistributor(supply.getDistributor().getName());
            currentSupply.getDistributor().setDistributorCode(supply.getDistributor().getCode());
            currentSupply.getDistributor().setPointType(supply.getDistributor().getPointType());
        }

        if (supply.getDatadis() != null) {
            if (currentSupply.getDatadis() == null) {
                currentSupply.setDatadis(new org.lucoenergia.conluz.infrastructure.admin.supply.datadis.SupplyDatadisEntity());
            }
            currentSupply.getDatadis().setThirdParty(supply.getDatadis().isThirdParty());
        }

        if (supply.getShelly() != null) {
            if (currentSupply.getShelly() == null) {
                currentSupply.setShelly(new SupplyShellyEntity());
            }
            currentSupply.getShelly().setShellyMac(supply.getShelly().getMac());
            currentSupply.getShelly().setShellyId(supply.getShelly().getId());
            currentSupply.getShelly().setShellyMqttPrefix(supply.getShelly().getMqttPrefix());
        }

        return mapper.map(repository.save(currentSupply));
    }
}
