package org.lucoenergia.conluz.infrastructure.shared.i18n;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

class MessageI18nTest extends BaseIntegrationTest {

    @Autowired
    private MessageSource messageSource;

    @Test
    void testMessage() {
        String message = messageSource.getMessage("error.admin.user.already.initialized", new Object[0],
                LocaleContextHolder.getLocale());
        Assertions.assertEquals("El usuario admin ya ha sido initializado.", message);
    }
}
