package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class DisableUserRepositoryDatabase implements DisableUserRepository {

    private final UserRepository userRepository;

    public DisableUserRepositoryDatabase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void disable(UserId id, Instant disabledAt) {
        Optional<UserEntity> entity = userRepository.findById(id.getId());
        if (entity.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        UserEntity user = entity.get();
        user.setEnabled(false);
        user.setDisabledAt(disabledAt);
        userRepository.save(user);
    }
}
