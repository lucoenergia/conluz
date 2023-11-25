package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.web.Assembler;
import org.springframework.stereotype.Component;

@Component
public class CreateUserAssembler implements Assembler<CreateUserBody, User> {

    @Override
    public User assemble(CreateUserBody request) {
        return new User(request.getId(), request.getNumber(), request.getFirstName(), request.getLastName(),
                request.getAddress(), request.getEmail(), request.getPhoneNumber(), true);
    }
}
