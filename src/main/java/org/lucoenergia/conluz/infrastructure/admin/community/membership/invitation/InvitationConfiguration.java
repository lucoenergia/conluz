package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.infrastructure.shared.web.PublicWebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the invitation link from the public web URL. A missing URL is already warned about at startup by the
 * password reset configuration, and each invitation it prevents is logged when it happens.
 */
@Configuration
@EnableConfigurationProperties(PublicWebProperties.class)
public class InvitationConfiguration {

    @Bean
    InvitationLink invitationLink(PublicWebProperties webProperties) {
        return InvitationLink.of(webProperties.publicUrl());
    }
}
