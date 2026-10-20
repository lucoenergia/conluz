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
 * The messages of invitation acceptance (#394) exist in both shipped locales. The Spanish ones address the member
 * informally, like the other messages a member reads before logging in.
 */
class InvitationAcceptanceMessagesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "messages.properties    | error.membership.invitation.token.invalid  | The invitation link is invalid or has expired. Ask whoever invited you for a new one.",
            "messages.properties    | error.membership.invitation.login.required | This invitation is for an account that already has a password. Log in to accept it.",
            "messages_es.properties | error.membership.invitation.token.invalid  | El enlace de invitación no es válido o ha caducado. Pide uno nuevo a quien te ha invitado.",
            "messages_es.properties | error.membership.invitation.login.required | Esta invitación es para una cuenta que ya tiene contraseña. Inicia sesión para aceptarla."
    })
    void eachLocaleHasTheMessage(String bundle, String key, String expected) throws IOException {
        Properties messages = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(bundle)) {
            messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertEquals(expected, messages.getProperty(key));
    }
}
