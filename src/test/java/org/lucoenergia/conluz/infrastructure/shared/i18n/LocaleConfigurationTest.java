package org.lucoenergia.conluz.infrastructure.shared.i18n;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

class LocaleConfigurationTest extends BaseIntegrationTest {

    @Autowired
    private LocaleConfiguration localeConfiguration;

    @Test
    void testDefaultLocale() {
        Assertions.assertEquals(localeConfiguration.getDefaultLocale().toLowerCase(), Locale.getDefault().toString());
        Assertions.assertEquals(localeConfiguration.getDefaultLocale().toLowerCase(), LocaleContextHolder.getLocale().toString());
    }
}
