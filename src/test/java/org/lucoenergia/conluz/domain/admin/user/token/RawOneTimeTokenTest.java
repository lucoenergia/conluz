package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawOneTimeTokenTest {

    private static final String VALUE = "q3Zr8VhJx0bWm2LkT9sYcN4aP7dE1fGuH6iO5jRtUvA";

    @Test
    void toString_doesNotRevealTheValue() {
        String printed = new RawOneTimeToken(VALUE).toString();

        assertFalse(printed.contains(VALUE.substring(0, 4)), "toString reveals the token");
        assertTrue("RawOneTimeToken[value=<redacted>]".equals(printed), "toString is not the redacted form");
    }

    @Test
    void stringConcatenation_doesNotRevealTheValue() {
        assertFalse(("token: " + new RawOneTimeToken(VALUE)).contains(VALUE), "Concatenation reveals the token");
    }

    @Test
    void value_returnsTheToken() {
        assertTrue(VALUE.equals(new RawOneTimeToken(VALUE).value()), "value() does not return the token");
    }

    @Test
    void rejectsNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> new RawOneTimeToken(null));
        assertThrows(IllegalArgumentException.class, () -> new RawOneTimeToken(""));
        assertThrows(IllegalArgumentException.class, () -> new RawOneTimeToken("  "));
    }
}
