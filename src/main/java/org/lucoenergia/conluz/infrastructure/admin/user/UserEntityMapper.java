package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Component
public class UserEntityMapper extends BaseMapper<UserEntity, User> {

    @Override
    public User map(UserEntity entity) {
        User user = new User();
        user.setId(entity.getId());
        user.setPersonalId(entity.getPersonalId());
        user.setNumber(entity.getNumber());
        user.setPassword(entity.getPassword());
        user.setFullName(entity.getFullName());
        user.setAddress(entity.getAddress());
        user.setEmail(entity.getEmail());
        user.setPhoneNumber(entity.getPhoneNumber());
        user.setEnabled(entity.isEnabled());
        user.setRole(entity.getRole());

        return user;
    }
}
