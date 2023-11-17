package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.stereotype.Component;

@Component
public class UserEntityMapper {

    public User map(UserEntity entity) {
        return new User(entity.getId(), entity.getNumber(), entity.getFirstName(), entity.getLastName(),
                entity.getAddress(), entity.getEmail(), entity.getPhoneNumber(),
                entity.getEnabled());
    }
}
