package org.lucoenergia.conluz.infrastructure.admin.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;

public class UserEntityMapperTest {

    private final UserEntityMapper mapper = new UserEntityMapper();

    @Test
    void testMap() {
        UserEntity entity = UserMother.randomUserEntity();

        User result = mapper.map(entity);

        Assertions.assertEquals(entity.getId(), result.getId());
        Assertions.assertEquals(entity.getNumber(), result.getNumber());
        Assertions.assertEquals(entity.getFullName(), result.getFullName());
        Assertions.assertEquals(entity.getAddress(), result.getAddress());
        Assertions.assertEquals(entity.getEmail(), result.getEmail());
        Assertions.assertEquals(entity.getPhoneNumber(), result.getPhoneNumber());
        Assertions.assertEquals(entity.getEnabled(), result.isEnabled());
    }
}
