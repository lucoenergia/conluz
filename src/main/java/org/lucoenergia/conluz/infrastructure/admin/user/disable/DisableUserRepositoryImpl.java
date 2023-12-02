package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DisableUserRepositoryImpl implements DisableUserRepository {

    private final UserRepository userRepository;

    public DisableUserRepositoryImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void disable(UserId id) {
        Optional<UserEntity> entity = userRepository.findById(id.getId());
        if (entity.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        UserEntity user = entity.get();
        user.setEnabled(false);
        userRepository.save(user);
    }
}
