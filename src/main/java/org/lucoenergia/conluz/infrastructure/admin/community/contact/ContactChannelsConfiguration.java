package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelsValidator;
import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the domain's contact channels validator with the email rule infrastructure provides.
 */
@Configuration
public class ContactChannelsConfiguration {

    @Bean
    ContactChannelsValidator contactChannelsValidator(ContactEmailValidity contactEmailValidity) {
        return new ContactChannelsValidator(contactEmailValidity);
    }
}
