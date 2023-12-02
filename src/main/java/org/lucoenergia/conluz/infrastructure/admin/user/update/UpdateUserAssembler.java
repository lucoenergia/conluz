package org.lucoenergia.conluz.infrastructure.admin.user.update;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UpdateUserAssembler {

    public User assemble(UUID uuid, UpdateUserBody request) {
        User user = new User();
        user.setId(uuid);
        user.setNumber(request.getNumber());
        user.setFullName(request.getFullName());
        user.setAddress(request.getAddress());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(request.getRole());
        return user;
    }
}
