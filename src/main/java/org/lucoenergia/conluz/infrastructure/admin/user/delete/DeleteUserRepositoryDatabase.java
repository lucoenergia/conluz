package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserRepository;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Repository
public class DeleteUserRepositoryDatabase implements DeleteUserRepository {

    private final UserRepository userRepository;

    public DeleteUserRepositoryDatabase(UserRepository userRepository) {
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
