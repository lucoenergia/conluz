package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.lucoenergia.conluz.domain.admin.user.password.PasswordBlocklist;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PasswordPolicyConfiguration {

    static final List<String> BLOCKLIST_RESOURCES = List.of(
            "password-blocklist/seclists-top-1m-min10.txt",
            "password-blocklist/curated-es.txt");

    @Bean
    public PasswordBlocklist passwordBlocklist() {
        return new ClasspathPasswordBlocklist(BLOCKLIST_RESOURCES);
    }

    @Bean
    public PasswordPolicy passwordPolicy(PasswordBlocklist passwordBlocklist) {
        return new PasswordPolicy(passwordBlocklist);
    }
}
