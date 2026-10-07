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
 * The messages of password recovery (#362) exist in both shipped locales. The Spanish ones address the member
 * informally.
 */
class PasswordResetMessagesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "messages.properties    | error.user.password.reset.token.invalid | The password reset link is invalid or has expired. Please request a new one.",
            "messages.properties    | email.password.reset.subject            | Recover your password",
            "messages.properties    | email.password.reset.body               | We have received a request to reset the password of your account.\\n\\nTo choose a new password, open this link:\\n{0}\\n\\nThe link is valid for 1 day and can only be used once.\\n\\nIf you did not ask for this, ignore this message: your password will not change.",
            "messages_es.properties | error.user.password.reset.token.invalid | El enlace para restablecer tu contraseña no es válido o ha caducado. Solicita uno nuevo.",
            "messages_es.properties | email.password.reset.subject            | Recupera tu contraseña",
            "messages_es.properties | email.password.reset.body               | Hemos recibido una solicitud para restablecer la contraseña de tu cuenta.\\n\\nPara elegir una contraseña nueva, abre este enlace:\\n{0}\\n\\nEl enlace es válido durante 1 día y solo puede usarse una vez.\\n\\nSi no lo has solicitado, ignora este mensaje: tu contraseña no cambiará."
    })
    void eachLocaleHasTheMessage(String bundle, String key, String expected) throws IOException {
        Properties messages = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(bundle)) {
            messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertEquals(expected.replace("\\n", "\n"), messages.getProperty(key));
    }
}
