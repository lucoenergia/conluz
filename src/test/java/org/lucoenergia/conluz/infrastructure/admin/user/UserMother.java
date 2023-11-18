package org.lucoenergia.conluz.infrastructure.admin.user;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.lucoenergia.conluz.domain.admin.user.User;

public class UserMother {

    public static UserEntity randomUserEntity() {
        return new UserEntity(RandomStringUtils.randomAlphabetic(9),
                RandomUtils.nextInt(),
                "$2a$12$" + RandomStringUtils.randomAlphabetic(53),
                RandomStringUtils.random(5, true, false),
                RandomStringUtils.random(10, true, false),
                RandomStringUtils.randomAlphabetic(30),
                RandomStringUtils.random(5, true, false) + "@" + RandomStringUtils.random(5, true, false) + ".com", "+34666333111",
                RandomUtils.nextBoolean()
        );
    }

    public static User randomUser() {
        return new User(RandomStringUtils.randomAlphabetic(9),
                RandomUtils.nextInt(),
                RandomStringUtils.random(5, true, false),
                RandomStringUtils.random(10, true, false),
                RandomStringUtils.randomAlphabetic(30),
                RandomStringUtils.random(5, true, false) + "@" + RandomStringUtils.random(5, true, false) + ".com",
                "+34666333111",
                RandomUtils.nextBoolean()
        );
    }

    public static String randomPassword() {
        return RandomStringUtils.random(16, true, true);
    }
}
