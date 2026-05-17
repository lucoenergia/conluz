package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyCannotBeCreatedException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.contract.SupplyContractEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.datadis.SupplyDatadisEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.distributor.SupplyDistributorEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.shelly.SupplyShellyEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class CreateSupplyRepositoryDatabase implements CreateSupplyRepository {

    private final SupplyRepository supplyRepository;
    private final UserRepository userRepository;
    private final SupplyEntityMapper supplyEntityMapper;
    private final UserEntityMapper userEntityMapper;

    public CreateSupplyRepositoryDatabase(SupplyRepository supplyRepository, UserRepository userRepository,
                                          SupplyEntityMapper supplyEntityMapper, UserEntityMapper userEntityMapper) {
        this.supplyRepository = supplyRepository;
        this.userRepository = userRepository;
        this.supplyEntityMapper = supplyEntityMapper;
        this.userEntityMapper = userEntityMapper;
    }

    @Override
    public Supply create(Supply supply, UserId id) {
        Optional<UserEntity> result = userRepository.findById(id.getId());
        if (result.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        if (supplyRepository.countByCode(supply.getCode()) > 0) {
            throw new SupplyAlreadyExistsException(SupplyCode.of(supply.getCode()));
        }

        UserEntity userEntity = result.get();
        UUID supplyId = UUID.randomUUID();

        SupplyShellyEntity shellyEntity = null;
        if (supply.getShelly() != null) {
            shellyEntity = new SupplyShellyEntity.Builder()
                    .withMacAddress(supply.getShelly().getMacAddress())
                    .withId(supply.getShelly().getId())
                    .withMqttPrefix(supply.getShelly().getMqttPrefix())
                    .build();
        }

        SupplyDatadisEntity datadisEntity = null;
        if (supply.getDatadis() != null) {
            datadisEntity = new SupplyDatadisEntity.Builder()
                    .withThirdParty(supply.getDatadis().isThirdParty())
                    .build();
        }

        SupplyDistributorEntity distributorEntity = null;
        if (supply.getDistributor() != null) {
            distributorEntity = new SupplyDistributorEntity.Builder()
                    .withName(supply.getDistributor().getName())
                    .withCode(supply.getDistributor().getCode())
                    .withPointType(supply.getDistributor().getPointType())
                    .build();
        }

        SupplyContractEntity contractEntity = null;
        if (supply.getContract() != null) {
            contractEntity = new SupplyContractEntity.Builder()
                    .withValidDateFrom(supply.getContract().getValidDateFrom())
                    .build();
        }

        SupplyEntity supplyEntity = new SupplyEntity.Builder()
                .withId(supplyId)
                .withCode(supply.getCode())
                .withName(supply.getName())
                .withAddress(supply.getAddress())
                .withAddressRef(supply.getAddressRef())
                .withPartitionCoefficient(supply.getPartitionCoefficient())
                .withEnabled(supply.getEnabled())
                .withShelly(shellyEntity)
                .withDatadis(datadisEntity)
                .withDistributor(distributorEntity)
                .withContract(contractEntity)
                .build();

        userEntity.addSupply(supplyEntity);

        userRepository.save(userEntity);

        Optional<SupplyEntity> newSupplyEntity = supplyRepository.findByCode(supply.getCode());
        if (newSupplyEntity.isEmpty()) {
            throw new SupplyCannotBeCreatedException();
        }

        Supply newSupply = supplyEntityMapper.map(newSupplyEntity.get());
        newSupply.setUser(userEntityMapper.map(userEntity));

        return newSupply;
    }
}
