package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.password.ChangePasswordRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
@Transactional
public class ChangePasswordRepositoryDatabase implements ChangePasswordRepository {

    private final UserRepository repository;

    public ChangePasswordRepositoryDatabase(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public void changePassword(UserId userId, String encodedPassword, Instant changedAt) {
        UserEntity user = repository.findById(userId.getId())
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Exactly three columns: the password, the flag it satisfies, and the instant that invalidates
        // every token issued before it.
        user.setPassword(encodedPassword);
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(changedAt);

        repository.save(user);
    }
}
