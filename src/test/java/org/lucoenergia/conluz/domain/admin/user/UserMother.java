package org.lucoenergia.conluz.domain.admin.user;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;

import java.util.UUID;

public class UserMother {

    public static UserEntity randomUserEntity() {
        return randomUserEntityWithPersonalId(RandomStringUtils.randomAlphabetic(9));
    }

    public static UserEntity randomUserEntityWithPersonalId(String personalId) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setPersonalId(personalId);
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
        return randomUserWithPersonalId(RandomStringUtils.randomAlphabetic(9));
    }

    public static User randomUserWithPersonalId(String personalId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPersonalId(personalId);
        user.setNumber(RandomUtils.nextInt());
        user.setFullName(RandomStringUtils.random(15, true, false));
        user.setAddress(RandomStringUtils.randomAlphabetic(30));
        user.setEmail(RandomStringUtils.random(5, true, false) + "@" + RandomStringUtils.random(5, true, false) + ".com");
        user.setPhoneNumber("+34666333111");
        user.setEnabled(RandomUtils.nextBoolean());
        user.setRole(Role.PARTNER);
        return user;
    }

    public static User randomUserWithId(UUID id) {
        User user = new User();
        user.setId(id);
        user.setPersonalId(RandomStringUtils.randomAlphabetic(9));
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
