package org.lucoenergia.conluz.infrastructure.admin.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;

public class UserEntityMapperTest {

    private final UserEntityMapper mapper = new UserEntityMapper();

    @Test
    void testMap() {
        UserEntity entity = new UserEntity("22123456Z", 1,
                "$2a$12$6H.wXn1D4wvpZlJ/fTNDu.pdIinw.LBw68JYVxYR86Dz/2HTXM4X2", "John",
                "Doe", "Fake Street 123", "johndoe@email.com", "+34666333111",
                true);

        User result = mapper.map(entity);

        Assertions.assertEquals(entity.getId(), result.getId());
        Assertions.assertEquals(entity.getNumber(), result.getNumber());
        Assertions.assertEquals(entity.getFirstName(), result.getFirstName());
        Assertions.assertEquals(entity.getLastName(), result.getLastName());
        Assertions.assertEquals(entity.getAddress(), result.getAddress());
        Assertions.assertEquals(entity.getEmail(), result.getEmail());
        Assertions.assertEquals(entity.getPhoneNumber(), result.getPhoneNumber());
        Assertions.assertEquals(entity.getEnabled(), result.getEnabled());
    }
}
