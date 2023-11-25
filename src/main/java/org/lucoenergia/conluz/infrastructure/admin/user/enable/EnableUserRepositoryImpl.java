package org.lucoenergia.conluz.infrastructure.admin.user.enable;

import org.lucoenergia.conluz.domain.admin.user.EnableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class EnableUserRepositoryImpl implements EnableUserRepository {

    private final UserRepository userRepository;

    public EnableUserRepositoryImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void enable(UserId id) {
        Optional<UserEntity> entity = userRepository.findById(id.getId());
        if (entity.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        UserEntity user = entity.get();
        user.setEnabled(true);
        userRepository.save(user);
    }
}
