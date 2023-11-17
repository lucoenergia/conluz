package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

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
    public User create(User user, String password) {
        String encodedPassword = passwordEncoder.encode(password);
        UserEntity entity = new UserEntity(user.getId(), encodedPassword, user.getFirstName(), user.getLastName(),
                user.getAddress(), user.getEmail(), user.getPhoneNumber(), true);

        return mapper.map(repository.save(entity));
    }
}
