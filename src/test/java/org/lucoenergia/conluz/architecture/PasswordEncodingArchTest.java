package org.lucoenergia.conluz.architecture;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.user.password.UserPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing that every user password is hashed through {@link UserPasswordEncoder}, which
 * checks the password policy first.
 *
 * <p>BCrypt only reads the first 72 bytes of its input. Calling {@link PasswordEncoder#encode} anywhere else
 * would open a path where a password skips the policy: an over-long value would then fail inside the encoder,
 * or a too-short one would be stored, instead of being rejected with the policy's error.</p>
 */
public class PasswordEncodingArchTest extends BaseArchTest {

    @Test
    void onlyUserPasswordEncoderMayHashPasswords() {
        noClasses()
                .that().doNotBelongToAnyOf(UserPasswordEncoder.class)
                .should().callMethodWhere(target(owner(assignableTo(PasswordEncoder.class)))
                        .and(target(name("encode"))))
                .because("every user password must be checked against the password policy before it is "
                        + "hashed; UserPasswordEncoder is the only place that does both")
                .check(IMPORTED_CLASSES);
    }
}
