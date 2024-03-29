package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Repository
public class CreateUserRepositoryImpl implements CreateUserRepository {

    private final UserRepository repository;
    private final UserEntityMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public CreateUserRepositoryImpl(UserRepository repository, UserEntityMapper mapper,
                                    PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User create(User user) {
        if (repository.existsByPersonalId(user.getPersonalId())) {
            throw new UserAlreadyExistsException(UserPersonalId.of(user.getPersonalId()));
        }
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        UserEntity entity = UserEntity.createNewUser(user, encodedPassword);

        return mapper.map(repository.save(entity));
    }
}
