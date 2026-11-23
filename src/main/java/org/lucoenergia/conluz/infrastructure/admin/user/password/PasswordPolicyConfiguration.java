package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordPolicyConfiguration {

    @Bean
    public PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }
}
