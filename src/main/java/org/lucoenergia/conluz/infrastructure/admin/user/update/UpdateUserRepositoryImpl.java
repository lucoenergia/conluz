package org.lucoenergia.conluz.infrastructure.admin.user.update;

import org.lucoenergia.conluz.domain.admin.user.UpdateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UpdateUserRepositoryImpl implements UpdateUserRepository {

    private final UserRepository repository;
    private final UserEntityMapper mapper;

    public UpdateUserRepositoryImpl(UserRepository repository, UserEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public User update(User user) {
        UserId id = new UserId(user.getId());
        Optional<UserEntity> result = repository.findById(user.getId());
        if (result.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        UserEntity currentUser = result.get();
        currentUser.setNumber(user.getNumber());
        currentUser.setFirstName(user.getFirstName());
        currentUser.setLastName(user.getLastName());
        currentUser.setEmail(user.getEmail());
        currentUser.setAddress(user.getAddress());
        currentUser.setPhoneNumber(user.getPhoneNumber());

        return mapper.map(repository.save(currentUser));
    }
}
