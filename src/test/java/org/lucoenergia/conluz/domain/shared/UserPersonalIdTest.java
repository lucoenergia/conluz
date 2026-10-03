package org.lucoenergia.conluz.domain.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserPersonalIdTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "12345678A",
            "12345678a",
            " 12345678A ",
            " 12345678 A ",
            "12345678-A",
            "12.345.678-A",
            "\t12345678A\n",
            "\r\n12345678\fA\u000B",
            " 12345678 A ",
            "1-2.3 4-5.6 7-8.a",
    })
    void normalisesEveryTypingVariantToTheSameValue(String variant) {
        assertEquals("12345678A", UserPersonalId.normalize(variant));
    }

    @Test
    void upperCasesNiesAndCifs() {
        assertEquals("X1234567L", UserPersonalId.normalize("x1234567-l"));
        assertEquals("B12345678", UserPersonalId.normalize("b-12.345.678"));
    }

    @Test
    void isIdempotent() {
        String once = UserPersonalId.normalize(" 12.345.678-a ");
        assertEquals(once, UserPersonalId.normalize(once));
    }

    @Test
    void keepsCharactersOutsideTheRemovedSet() {
        assertEquals("12345678_A/", UserPersonalId.normalize("12345678_a/"));
    }

    @Test
    void doesNotValidateTheFormat() {
        assertEquals("NOTANIF", UserPersonalId.normalize("not a nif"));
        assertEquals("", UserPersonalId.normalize(" - . "));
    }

    @Test
    void returnsNullForNull() {
        assertNull(UserPersonalId.normalize(null));
    }

    @Test
    void ofKeepsTheValueAsGiven() {
        assertEquals(" 12345678-a ", UserPersonalId.of(" 12345678-a ").getPersonalId());
    }
}
