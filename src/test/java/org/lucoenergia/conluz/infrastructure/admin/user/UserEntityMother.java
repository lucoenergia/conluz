package org.lucoenergia.conluz.infrastructure.admin.user;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;

public class UserEntityMother {

    public static UserEntity random() {
        return new UserEntity(RandomStringUtils.randomAlphabetic(9),
                "$2a$12$" + RandomStringUtils.randomAlphabetic(53),
                RandomStringUtils.random(5, true, false),
                RandomStringUtils.random(10, true, false),
                RandomStringUtils.randomAlphabetic(30),
                RandomStringUtils.random(5, true, false) + "@" + RandomStringUtils.random(5, true, false) + ".com", "+34666333111",
                RandomUtils.nextBoolean()
        );
    }
}
