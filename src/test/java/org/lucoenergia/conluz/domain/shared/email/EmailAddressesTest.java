package org.lucoenergia.conluz.domain.shared.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailAddressesTest {

    @Test
    void nullStaysNull() {
        assertNull(EmailAddresses.blankToNull(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " \t ", "\n", "\r\n", "\f", "\u000B", " \t\n\r\f\u000B"})
    void anEmptyOrBlankValue_isNoEmail(String blank) {
        assertNull(EmailAddresses.blankToNull(blank));
    }

    @ParameterizedTest
    @ValueSource(strings = {"member@example.org", " member@example.org ", "not-an-email", " "})
    void anyOtherValue_isKeptAsGiven(String value) {
        assertEquals(value, EmailAddresses.blankToNull(value));
    }
}
