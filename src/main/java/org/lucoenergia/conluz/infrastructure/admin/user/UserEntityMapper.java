package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserEntityMapper {

    public User map(UserEntity entity) {
        return new User(entity.getId(), entity.getNumber(), entity.getFirstName(), entity.getLastName(),
                entity.getAddress(), entity.getEmail(), entity.getPhoneNumber(),
                entity.getEnabled());
    }

    public List<User> mapList(List<UserEntity> entities) {
        return entities.stream()
                .map(this::map)
                .toList();
    }
}
