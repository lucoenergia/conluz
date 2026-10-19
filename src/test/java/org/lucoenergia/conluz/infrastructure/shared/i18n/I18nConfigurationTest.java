package org.lucoenergia.conluz.infrastructure.shared.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class I18nConfigurationTest {

    private static final String ENGLISH =
            "Authentication is required. Please provide valid credentials and try again.";
    private static final String SPANISH =
            "Se requiere autenticación. Por favor, proporcione credenciales válidas e inténtelo de nuevo.";

    private final MessageSource messageSource = new I18nConfiguration().messageSource();
    private Locale serverLocale;

    @BeforeEach
    void setServerLocaleToSpanish() {
        // As LocaleConfiguration sets it with the default configuration
        serverLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("es"));
    }

    @AfterEach
    void restoreServerLocale() {
        Locale.setDefault(serverLocale);
    }

    @Test
    void english_isAnsweredInEnglish_whateverTheServerLanguage() {
        assertThat(messageSource.getMessage("error.unauthorized", null, Locale.ENGLISH)).isEqualTo(ENGLISH);
    }

    @Test
    void aLanguageWithoutItsOwnMessages_isAnsweredInEnglish() {
        assertThat(messageSource.getMessage("error.unauthorized", null, Locale.FRENCH)).isEqualTo(ENGLISH);
    }

    @Test
    void spanish_isAnsweredInSpanish() {
        assertThat(messageSource.getMessage("error.unauthorized", null, Locale.forLanguageTag("es-ES"))).isEqualTo(SPANISH);
    }
}
