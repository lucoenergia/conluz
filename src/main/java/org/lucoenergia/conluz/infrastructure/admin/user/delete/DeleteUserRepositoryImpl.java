package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.lucoenergia.conluz.domain.admin.user.DeleteUserRepository;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DeleteUserRepositoryImpl implements DeleteUserRepository {

    private final UserRepository userRepository;

    public DeleteUserRepositoryImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void delete(UserId id) {
        Optional<UserEntity> entity = userRepository.findById(id.getId());
        if (entity.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        userRepository.delete(entity.get());
    }
}
