package org.lucoenergia.conluz.infrastructure.shared.i18n;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

class LocaleConfigurationTest extends BaseIntegrationTest {

    @Autowired
    private LocaleConfiguration localeConfiguration;

    @Test
    void testDefaultLocale() {
        Assertions.assertEquals(localeConfiguration.getDefaultLocale().toLowerCase(), Locale.getDefault().toString());
    }
}
