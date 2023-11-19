package org.lucoenergia.conluz.infrastructure.admin.user;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.lucoenergia.conluz.domain.admin.user.User;

public class UserMother {

    public static UserEntity randomUserEntity() {
        return randomUserEntityWithId(RandomStringUtils.randomAlphabetic(9));
    }

    public static UserEntity randomUserEntityWithId(String id) {
        return new UserEntity(id,
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
        return randomUserWithId(RandomStringUtils.randomAlphabetic(9));
    }

    public static User randomUserWithId(String id) {
        return new User(id,
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
