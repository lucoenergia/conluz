package org.lucoenergia.conluz.infrastructure.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KWhRoundingTest {

    @Test
    @DisplayName("EVL-001 AC2 the noise a floating-point sum carries is rounded away")
    void floatingPointNoiseIsRoundedAway() {
        assertEquals(0.30000000000000004, 0.1 + 0.2);

        assertEquals(0.3, KWhRounding.round(0.1 + 0.2));
    }

    @Test
    @DisplayName("EVL-001 a tie at the fourth decimal rounds half-up")
    void aTieRoundsHalfUp() {
        assertEquals(0.001, KWhRounding.round(0.0005));
        assertEquals(1.001, KWhRounding.round(1.0005));
        assertEquals(2.346, KWhRounding.round(2.3455));
    }

    @Test
    @DisplayName("EVL-001 a value below the tie rounds down")
    void belowTheTieRoundsDown() {
        assertEquals(0.345, KWhRounding.round(0.34549));
        assertEquals(0.0, KWhRounding.round(0.0004));
    }

    @Test
    @DisplayName("EVL-001 zero stays zero")
    void zeroStaysZero() {
        assertEquals(0.0, KWhRounding.round(0.0));
    }

    @Test
    @DisplayName("EVL-001 a value with 3 decimals or fewer is unchanged")
    void anExactValueIsUnchanged() {
        assertEquals(1.25, KWhRounding.round(1.25));
        assertEquals(12.5, KWhRounding.round(12.5));
        assertEquals(161.308, KWhRounding.round(161.308));
        assertEquals(1155.0, KWhRounding.round(1155.0));
    }
}
