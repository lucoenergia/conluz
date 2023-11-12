package org.lucoenergia.conluz.infrastructure.admin;

import org.lucoenergia.conluz.domain.admin.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GetUserRepositoryImpl implements GetUserRepository {

    private final UserRepository userRepository;
    private final UserEntityMapper userEntityMapper;

    public GetUserRepositoryImpl(UserRepository userRepository, UserEntityMapper userEntityMapper) {
        this.userRepository = userRepository;
        this.userEntityMapper = userEntityMapper;
    }

    @Override
    public Optional<User> findById(UserId id) {
        Optional<UserEntity> entity = userRepository.findById(id.getId());

        if (entity.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(userEntityMapper.map(entity.get()));
    }
}
