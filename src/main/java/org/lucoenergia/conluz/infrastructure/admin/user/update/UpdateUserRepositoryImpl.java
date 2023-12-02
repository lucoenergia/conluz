package org.lucoenergia.conluz.infrastructure.admin.user.update;

import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
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
        UserId id = UserId.of(user.getId());
        Optional<UserEntity> result = repository.findById(user.getId());
        if (result.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        UserEntity currentUser = result.get();
        currentUser.setNumber(user.getNumber());
        currentUser.setFullName(user.getFullName());
        currentUser.setEmail(user.getEmail());
        currentUser.setAddress(user.getAddress());
        currentUser.setPhoneNumber(user.getPhoneNumber());
        currentUser.setRole(user.getRole());

        return mapper.map(repository.save(currentUser));
    }
}
