package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CreateSupplyRepositoryImpl implements CreateSupplyRepository {

    private final SupplyRepository supplyRepository;
    private final UserRepository userRepository;
    private final SupplyEntityMapper supplyEntityMapper;
    private final UserEntityMapper userEntityMapper;

    public CreateSupplyRepositoryImpl(SupplyRepository supplyRepository, UserRepository userRepository,
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
        UserEntity userEntity = result.get();
        SupplyEntity supplyEntity = new SupplyEntity(supply.getId(), supply.getName(), supply.getAddress(),
                supply.getPartitionCoefficient(), supply.getEnabled());

        userEntity.addSupply(supplyEntity);

        userRepository.save(userEntity);

        Supply newSupply = supplyEntityMapper.map(supplyRepository.findById(supply.getId()).get());
        newSupply.setUser(userEntityMapper.map(userEntity));

        return newSupply;
    }
}
