package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import org.lucoenergia.conluz.infrastructure.admin.user.password.UserPasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Refuses an account without a password (#389, ADR-0003 §7) exactly as it refuses a wrong password: the same
 * {@link BadCredentialsException}, after one BCrypt comparison.
 *
 * <p>Left to {@link DaoAuthenticationProvider}, a {@code null} stored password would reach
 * {@code BCryptPasswordEncoder#matches}, which returns at once without running BCrypt and logs
 * {@code Empty encoded password}: such a login would be measurably faster than any other failed login, and would
 * reveal that the account exists but was never accepted. Instead, the presented password is compared with a hash of
 * a random secret, computed once and then forgotten, so that no password can ever match it. Spring's own
 * equivalent, for unknown users, is not reused: it is private, and it hashes a public constant.</p>
 */
public class PasswordlessAccountAuthenticationProvider extends DaoAuthenticationProvider {

    private static final int SECRET_BYTES = 32;

    private final String passwordlessHash;

    public PasswordlessAccountAuthenticationProvider(UserDetailsService userDetailsService,
                                                     PasswordEncoder passwordEncoder,
                                                     UserPasswordEncoder userPasswordEncoder) {
        super(userDetailsService);
        setPasswordEncoder(passwordEncoder);
        this.passwordlessHash = userPasswordEncoder.encode(randomSecret());
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails,
                                                  UsernamePasswordAuthenticationToken authentication)
            throws AuthenticationException {
        String storedPassword = userDetails.getPassword();
        if (authentication.getCredentials() != null && (storedPassword == null || storedPassword.isEmpty())) {
            // Spends the time of a real comparison, whose result is known in advance
            getPasswordEncoder().matches(authentication.getCredentials().toString(), passwordlessHash);
            this.logger.debug("Failed to authenticate since password does not match stored value");
            throw new BadCredentialsException(this.messages
                    .getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
        }
        super.additionalAuthenticationChecks(userDetails, authentication);
    }

    /**
     * 43 URL-safe characters, which the password policy accepts and BCrypt reads in full.
     */
    private static String randomSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}
