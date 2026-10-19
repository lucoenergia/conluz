package org.lucoenergia.conluz.domain.admin.user;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

    /**
     * Numbers handed out in sequence, so two fixtures of the same run never share a personal ID. They
     * start at random in a range no hard-coded DNI of the tests falls in (90,000,000 to 99,887,765),
     * leaving room for close to 900,000 fixtures per run.
     */
    private static final AtomicInteger NEXT_DNI_NUMBER =
            new AtomicInteger(90_000_000 + RandomUtils.nextInt(0, 9_000_000));

    /**
     * A valid DNI (#398), already in the normalised form the application stores (upper case, no
     * separators), so a fixture saved straight through the JPA repository is found by the normalising
     * lookups.
     */
    public static String randomPersonalId() {
        return validDni(NEXT_DNI_NUMBER.getAndIncrement());
    }

    /**
     * The DNI of an 8-digit number: the number, zero-padded, followed by its control letter.
     */
    public static String validDni(int number) {
        return String.format(Locale.ROOT, "%08d", number) + dniLetter(number);
    }

    /**
     * The control letter of a DNI number.
     */
    public static char dniLetter(int number) {
        return DNI_LETTERS.charAt(number % 23);
    }

    public static String randomPassword() {
        return RandomStringUtils.random(16, true, true);
    }
}
