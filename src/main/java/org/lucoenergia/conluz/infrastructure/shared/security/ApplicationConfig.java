package org.lucoenergia.conluz.infrastructure.shared.security;

import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.UserDetailsServiceFromDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ApplicationConfig {

    private final GetUserRepository getUserRepository;
    private final GetMembershipsRepository getMembershipsRepository;

    public ApplicationConfig(GetUserRepository getUserRepository,
                             GetMembershipsRepository getMembershipsRepository) {
        this.getUserRepository = getUserRepository;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService());
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        // Check the password even when the account is disabled, so that a disabled account takes as long to
        // reject as a wrong password and its state cannot be told from the response time. This is the current
        // default; it is set explicitly so that a change of default cannot reopen the difference unnoticed.
        authenticationProvider.setAlwaysPerformAdditionalChecksOnUser(true);
        return authenticationProvider;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new UserDetailsServiceFromDatabase(getUserRepository, getMembershipsRepository);
    }
}
