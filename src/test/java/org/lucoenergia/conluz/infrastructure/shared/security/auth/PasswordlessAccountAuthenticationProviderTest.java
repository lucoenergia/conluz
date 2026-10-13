package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.user.password.UserPasswordEncoder;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PasswordlessAccountAuthenticationProviderTest {

    private static final String PRESENTED = "any password at all, typed at login";

    // The lowest cost BCrypt accepts, to keep the test fast
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));
    private final UserPasswordEncoder userPasswordEncoder = new UserPasswordEncoder(passwordEncoder);

    @Test
    @DisplayName("ACC-001 a passwordless account is refused with the bad credentials exception")
    void aPasswordlessAccount_isRefusedWithTheBadCredentialsException() {
        PasswordlessAccountAuthenticationProvider provider = provider(passwordlessUser());

        BadCredentialsException refusal = assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(token(PRESENTED)));

        assertEquals("Bad credentials", refusal.getMessage());
    }

    @Test
    @DisplayName("ACC-001 each provider compares against its own random hash")
    void eachProvider_comparesAgainstItsOwnRandomHash() {
        String first = comparedHash(provider(passwordlessUser()));
        String second = comparedHash(provider(passwordlessUser()));

        assertTrue(first.startsWith("$2a$04$"), first);
        assertNotEquals(first, second);
    }

    @Test
    void anAccountWithAPassword_isStillCheckedAgainstIt() {
        User user = UserMother.randomUser();
        String rawPassword = user.getPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.enable();
        PasswordlessAccountAuthenticationProvider provider = provider(user);

        Authentication authentication = provider.authenticate(token(rawPassword));

        assertTrue(authentication.isAuthenticated());
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(token(PRESENTED)));
    }

    private String comparedHash(PasswordlessAccountAuthenticationProvider provider) {
        clearInvocations(passwordEncoder);
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(token(PRESENTED)));
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, times(1)).matches(eq(PRESENTED), hash.capture());
        return hash.getValue();
    }

    private PasswordlessAccountAuthenticationProvider provider(User user) {
        return new PasswordlessAccountAuthenticationProvider(username -> user, passwordEncoder, userPasswordEncoder);
    }

    private static User passwordlessUser() {
        User user = UserMother.randomUser();
        user.setPassword(null);
        user.enable();
        return user;
    }

    private static UsernamePasswordAuthenticationToken token(String password) {
        return new UsernamePasswordAuthenticationToken("12345678A", password);
    }
}
