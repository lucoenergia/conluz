package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNumberRequiredException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicyViolation;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicyViolationException;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class CreateUserRepositoryImplTest extends BaseIntegrationTest {

    @Autowired
    private CreateUserRepository repository;
    @Autowired
    private GetUserRepository getUserRepository;

    @Test
    void testCreate() {
        User user = UserMother.randomUser();

        User newUser = repository.create(user);

        Assertions.assertEquals(user.getPersonalId(), newUser.getPersonalId());
        Assertions.assertEquals(user.getNumber(), newUser.getNumber());
        Assertions.assertEquals(user.getFullName(), newUser.getFullName());
        Assertions.assertNotNull(newUser.getPassword());
        Assertions.assertEquals(user.getUsername(), newUser.getUsername());
        Assertions.assertEquals(user.getAddress(), newUser.getAddress());
        Assertions.assertEquals(user.getEmail(), newUser.getEmail());
        Assertions.assertEquals(user.getPhoneNumber(), newUser.getPhoneNumber());
    }

    @Test
    @DisplayName("ACC-004 a user without a password is refused by the password policy, and nothing is stored")
    void aUserWithoutAPassword_isRefusedByThePasswordPolicy_andNothingIsStored() {
        User user = UserMother.randomUser();
        user.setPassword(null);

        PasswordPolicyViolationException refusal = Assertions.assertThrows(PasswordPolicyViolationException.class,
                () -> repository.create(user));

        Assertions.assertEquals(PasswordPolicyViolation.TOO_SHORT, refusal.getRule());
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId())).isEmpty());
    }

    @Test
    @DisplayName("ACC-004 a user without a number is refused, and nothing is stored")
    void aUserWithoutANumber_isRefused_andNothingIsStored() {
        User user = UserMother.randomUser();
        user.setNumber(null);

        Assertions.assertThrows(UserNumberRequiredException.class, () -> repository.create(user));

        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId())).isEmpty());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", " \t\n\r\f\u000B "})
    @DisplayName("ACC-003 a user created without email, or with an empty or blank one, is stored with no email")
    void aMissingEmptyOrBlankEmail_isStoredAsNull(String email) {
        User user = UserMother.randomUser();
        user.setEmail(email);

        User newUser = repository.create(user);

        Assertions.assertNull(newUser.getEmail());
        Assertions.assertNull(getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId()))
                .orElseThrow().getEmail());
    }
}
