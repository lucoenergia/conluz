package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The only way a user password is hashed. Every password is checked against the {@link PasswordPolicy} before it
 * reaches BCrypt, so an over-long value is rejected with the policy's error instead of being handed to an encoder
 * that only reads its first 72 bytes. {@code PasswordEncodingArchTest} keeps every other class from calling
 * {@link PasswordEncoder#encode} directly.
 */
@Component
public class UserPasswordEncoder {

    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public UserPasswordEncoder(PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy) {
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    /**
     * @throws org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicyViolationException if the password
     *                                                                                            does not satisfy the policy
     */
    public String encode(String rawPassword) {
        passwordPolicy.check(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
