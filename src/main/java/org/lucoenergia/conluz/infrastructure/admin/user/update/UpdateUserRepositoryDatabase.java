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
import java.util.UUID;

@Repository
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
        UserEntity currentUser = result.get();
        currentUser.setNumber(user.getNumber());
        currentUser.setPersonalId(user.getPersonalId());
        currentUser.setFullName(user.getFullName());
        currentUser.setEmail(user.getEmail());
        currentUser.setAddress(user.getAddress());
        currentUser.setPhoneNumber(user.getPhoneNumber());
        currentUser.setRole(user.getRole());

        return mapper.map(repository.save(currentUser));
    }
}
