package org.lucoenergia.conluz.infrastructure.admin;

import org.lucoenergia.conluz.domain.admin.User;
import org.springframework.stereotype.Component;

@Component
public class UserEntityMapper {

    public User map(UserEntity entity) {
        return new User(entity.getId(), entity.getFirstName(), entity.getLastName(),
                entity.getAddress(), entity.getEmail(), entity.getPhoneNumber(),
                entity.getEnabled());
    }
}
