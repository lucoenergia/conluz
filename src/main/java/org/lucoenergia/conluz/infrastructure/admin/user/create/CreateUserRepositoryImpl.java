package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserPersonalIdUniqueConstraint;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.password.UserPasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Repository
public class CreateUserRepositoryImpl implements CreateUserRepository {

    private final UserRepository repository;
    private final UserEntityMapper mapper;
    private final UserPasswordEncoder passwordEncoder;

    public CreateUserRepositoryImpl(UserRepository repository, UserEntityMapper mapper,
                                    UserPasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User create(User user) {
        String personalId = UserPersonalId.normalize(user.getPersonalId());
        if (repository.existsByPersonalId(personalId)) {
            throw new UserAlreadyExistsException();
        }
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        UserEntity entity = UserEntity.createNewUser(user, encodedPassword);
        entity.setPersonalId(personalId);

        try {
            // Flushed here so a concurrent duplicate that passed the check above is rejected by the
            // unique constraint now, where it can be told apart from other integrity errors.
            return mapper.map(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throw UserPersonalIdUniqueConstraint.translate(e);
        }
    }
}
