package org.lucoenergia.conluz.infrastructure.admin.user.update;

import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserPersonalIdUniqueConstraint;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class UpdateUserRepositoryDatabase implements UpdateUserRepository {

    private final UserRepository repository;
    private final UserEntityMapper mapper;

    public UpdateUserRepositoryDatabase(UserRepository repository, UserEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public User update(User user) {
        UUID userUuid = user.getId();
        Optional<UserEntity> result = repository.findById(userUuid);
        if (result.isEmpty()) {
            throw new UserNotFoundException(UserId.of(userUuid));
        }
        // Checked before the managed entity is modified: the query would otherwise auto-flush the
        // new personal ID and fail on the unique constraint instead of answering the question.
        String personalId = UserPersonalId.normalize(user.getPersonalId());
        if (repository.existsByPersonalIdAndIdNot(personalId, userUuid)) {
            throw new UserAlreadyExistsException();
        }
        UserEntity currentUser = result.get();
        currentUser.setNumber(user.getNumber());
        currentUser.setPersonalId(personalId);
        currentUser.setFullName(user.getFullName());
        currentUser.setEmail(user.getEmail());
        currentUser.setAddress(user.getAddress());
        currentUser.setPhoneNumber(user.getPhoneNumber());

        try {
            return mapper.map(repository.saveAndFlush(currentUser));
        } catch (DataIntegrityViolationException e) {
            throw UserPersonalIdUniqueConstraint.translate(e);
        }
    }
}
