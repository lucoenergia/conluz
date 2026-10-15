package org.lucoenergia.conluz.infrastructure.admin.user.update;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserEmailRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class UpdateUserEmailRepositoryDatabase implements UpdateUserEmailRepository {

    private final UserRepository repository;

    public UpdateUserEmailRepositoryDatabase(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public void setEmail(UserId userId, String email) {
        UserEntity user = repository.findById(userId.getId())
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setEmail(email);
        repository.save(user);
    }
}
