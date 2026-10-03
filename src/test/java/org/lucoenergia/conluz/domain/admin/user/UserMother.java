package org.lucoenergia.conluz.domain.admin.user;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;

import java.util.Locale;
import java.util.UUID;

public class UserMother {

    public static UserEntity randomUserEntity() {
        return randomUserEntityWithPersonalId(randomPersonalId());
    }

    public static UserEntity randomUserEntityWithPersonalId(String personalId) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setPassword(randomPassword());
        user.setPersonalId(personalId);
        user.setPassword("$2a$12$" + RandomStringUtils.randomAlphabetic(53));
        user.setNumber(RandomUtils.nextInt());
        user.setFullName(RandomStringUtils.random(15, true, false));
        user.setAddress(RandomStringUtils.randomAlphabetic(30));
        user.setEmail(RandomStringUtils.random(5, true, false) + "@" + RandomStringUtils.random(5, true, false) + ".com");
        user.setPhoneNumber("+34666333111");
        user.setEnabled(RandomUtils.nextBoolean());
        return user;
    }

    public static User randomUser() {
        return randomUserWithPersonalId(randomPersonalId());
    }

    public static User randomUserWithPersonalId(String personalId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPassword(randomPassword());
        user.setPersonalId(personalId);
        user.setNumber(RandomUtils.nextInt());
        user.setFullName(RandomStringUtils.random(15, true, false));
        user.setAddress(RandomStringUtils.randomAlphabetic(30));
        user.setEmail(RandomStringUtils.random(5, true, false) + "@" + RandomStringUtils.random(5, true, false) + ".com");
        user.setPhoneNumber("+34666333111");
        user.setEnabled(RandomUtils.nextBoolean());
        return user;
    }

    public static User randomUserWithId(UUID id) {
        User user = new User();
        user.setId(id);
        user.setPassword(randomPassword());
        user.setPersonalId(randomPersonalId());
        user.setNumber(RandomUtils.nextInt());
        user.setFullName(RandomStringUtils.random(15, true, false));
        user.setAddress(RandomStringUtils.randomAlphabetic(30));
        user.setEmail(RandomStringUtils.random(5, true, false) + "@" + RandomStringUtils.random(5, true, false) + ".com");
        user.setPhoneNumber("+34666333111");
        user.setEnabled(RandomUtils.nextBoolean());
        return user;
    }

    /**
     * Already in the normalised form the application stores (upper case, no separators), so a
     * fixture saved straight through the JPA repository is found by the normalising lookups.
     */
    public static String randomPersonalId() {
        return RandomStringUtils.randomAlphabetic(9).toUpperCase(Locale.ROOT);
    }

    public static String randomPassword() {
        return RandomStringUtils.random(16, true, true);
    }
}
