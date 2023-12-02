package org.lucoenergia.conluz.infrastructure.admin.user;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;

public class UserMother {

    public static UserEntity randomUserEntity() {
        return randomUserEntityWithId(RandomStringUtils.randomAlphabetic(9));
    }

    public static UserEntity randomUserEntityWithId(String id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setPassword("$2a$12$" + RandomStringUtils.randomAlphabetic(53));
        user.setNumber(RandomUtils.nextInt());
        user.setFullName(RandomStringUtils.random(15, true, false));
        user.setAddress(RandomStringUtils.randomAlphabetic(30));
        user.setEmail(RandomStringUtils.random(5, true, false) + "@" + RandomStringUtils.random(5, true, false) + ".com");
        user.setPhoneNumber("+34666333111");
        user.setEnabled(RandomUtils.nextBoolean());
        user.setRole(Role.PARTNER);
        return user;
    }

    public static User randomUser() {
        return randomUserWithId(RandomStringUtils.randomAlphabetic(9));
    }

    public static User randomUserWithId(String id) {
        User user = new User();
        user.setId(id);
        user.setNumber(RandomUtils.nextInt());
        user.setFullName(RandomStringUtils.random(15, true, false));
        user.setAddress(RandomStringUtils.randomAlphabetic(30));
        user.setEmail(RandomStringUtils.random(5, true, false) + "@" + RandomStringUtils.random(5, true, false) + ".com");
        user.setPhoneNumber("+34666333111");
        user.setEnabled(RandomUtils.nextBoolean());
        user.setRole(Role.PARTNER);
        return user;
    }

    public static String randomPassword() {
        return RandomStringUtils.random(16, true, true);
    }
}
