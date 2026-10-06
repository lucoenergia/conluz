package org.lucoenergia.conluz.infrastructure.shared.i18n;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The messages of the password change refusals (#342) exist in both shipped locales.
 */
class PasswordChangeMessagesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "messages.properties    | error.user.password.unchanged       | The new password must be different from the current one.",
            "messages.properties    | error.user.password.change.required | You must change your password before you can continue.",
            "messages_es.properties | error.user.password.unchanged       | La nueva contraseña debe ser distinta de la actual.",
            "messages_es.properties | error.user.password.change.required | Debe cambiar su contraseña antes de continuar."
    })
    void eachLocaleHasTheMessage(String bundle, String key, String expected) throws IOException {
        Properties messages = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(bundle)) {
            messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertEquals(expected, messages.getProperty(key));
    }
}
