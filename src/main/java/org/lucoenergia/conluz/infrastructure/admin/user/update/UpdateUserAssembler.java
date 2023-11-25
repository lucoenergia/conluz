package org.lucoenergia.conluz.infrastructure.admin.user.update;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.stereotype.Component;

@Component
public class UpdateUserAssembler {

    public User assemble(String userId, UpdateUserBody request) {
        return new User(userId, request.getNumber(), request.getFirstName(), request.getLastName(),
                request.getAddress(), request.getEmail(), request.getPhoneNumber());
    }
}
