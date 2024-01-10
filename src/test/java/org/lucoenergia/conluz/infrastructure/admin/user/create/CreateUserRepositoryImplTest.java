package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class CreateUserRepositoryImplTest extends BaseIntegrationTest {

    @Autowired
    private CreateUserRepository repository;

    @Test
    void testCreate() {
        User user = UserMother.randomUser();

        User newUser = repository.create(user);

        Assertions.assertEquals(user.getPersonalId(), newUser.getPersonalId());
        Assertions.assertEquals(user.getNumber(), newUser.getNumber());
        Assertions.assertEquals(user.getFullName(), newUser.getFullName());
        Assertions.assertEquals(user.getRole(), newUser.getRole());
        Assertions.assertNotNull(newUser.getPassword());
        Assertions.assertEquals(user.getUsername(), newUser.getUsername());
        Assertions.assertEquals(user.getAddress(), newUser.getAddress());
        Assertions.assertEquals(user.getEmail(), newUser.getEmail());
        Assertions.assertEquals(user.getPhoneNumber(), newUser.getPhoneNumber());
    }
}
