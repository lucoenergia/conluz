package org.lucoenergia.conluz.infrastructure.admin.operator;

import org.lucoenergia.conluz.domain.admin.operator.OperatorContactValidator;
import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the domain's operator contact validator with the email rule infrastructure provides.
 */
@Configuration
public class OperatorContactConfiguration {

    @Bean
    OperatorContactValidator operatorContactValidator(ContactEmailValidator contactEmailValidator) {
        return new OperatorContactValidator(contactEmailValidator);
    }
}
