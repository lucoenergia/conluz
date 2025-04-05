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
        SupplyEntity supplyEntity = new SupplyEntity.Builder()
                .withId(UUID.randomUUID())
                .withCode(supply.getCode())
                .withName(supply.getName())
                .withAddress(supply.getAddress())
                .withPartitionCoefficient(supply.getPartitionCoefficient())
                .withEnabled(supply.getEnabled())

                .withDatadisValidDateFrom(supply.getValidDateFrom())
                .withDatadisDistributor(supply.getDistributor())
                .withDatadisDistributorCode(supply.getDistributorCode())
                .withDatadisPointType(supply.getPointType())
                .withDatadisIsThirdParty(supply.isThirdParty())

                .withShellyMac(supply.getShellyMac())
                .withShellyId(supply.getShellyId())
                .withShellyMqttPrefix(supply.getShellyMqttPrefix())

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
