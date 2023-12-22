package org.lucoenergia.conluz.infrastructure.shared.i18n;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

class LocaleConfigurationTest extends BaseIntegrationTest {

    @Autowired
    private CustomLocaleResolver customLocaleResolver;

    @Test
    void testDefaultLocale() {
        Assertions.assertEquals(customLocaleResolver.getDefaultLocale(), Locale.getDefault().toString());
    }
}
