package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import org.lucoenergia.conluz.infrastructure.shared.email.EmailProperties;
import org.lucoenergia.conluz.infrastructure.shared.web.PublicWebProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the password reset link from the public web URL, and warns once at startup when emails are being sent but
 * no recovery email could ever be: without that URL there is nothing to link to.
 */
@Configuration
@EnableConfigurationProperties({PublicWebProperties.class, EmailProperties.class})
public class PasswordResetConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetConfiguration.class);

    @Bean
    PasswordResetLink passwordResetLink(PublicWebProperties webProperties, EmailProperties emailProperties) {
        PasswordResetLink link = PasswordResetLink.of(webProperties.publicUrl());
        if (!link.isConfigured() && emailProperties.isEnabled()) {
            LOGGER.warn("Email sending is enabled but CONLUZ_PUBLIC_WEB_URL is missing or invalid; "
                    + "no password reset email will be sent");
        }
        return link;
    }
}
