package org.lucoenergia.conluz.domain.production.plant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlantCilTest {

    private static final String CIL = "ES0031406912345678AB1F001";

    @ParameterizedTest
    @ValueSource(strings = {
            "ES0031406912345678AB1F001",
            " es0031406912345678ab1f001 ",
            "ES00 3140 6912 3456 78AB 1F00 1",
            "\tES0031406912345678AB1F001\r\n",
            // No-break space at both ends and inside
            " ES0031406912 345678AB1F001 ",
            // Narrow no-break space, figure space and ideographic space
            "ES003140 6912345678 AB1F001　"
    })
    @DisplayName("PLT-004 AC4 the CIL is upper-cased and stripped of every whitespace and space character")
    void theCilIsUpperCasedAndStrippedOfEveryWhitespaceAndSpaceCharacter(String raw) {
        assertEquals(CIL, PlantCil.normalise(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", " ", "  　"})
    @DisplayName("PLT-004 AC6 a CIL that is empty once normalised is no CIL")
    void aCilThatIsEmptyOnceNormalisedIsNoCil(String raw) {
        assertNull(PlantCil.normalise(raw));
    }

    @Test
    @DisplayName("PLT-004 AC6 no CIL is no CIL")
    void noCilIsNoCil() {
        assertNull(PlantCil.normalise(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 24 and 26 characters
            "ES0031406912345678AB1F00", "ES0031406912345678AB1F0011",
            // A character that is neither a letter nor a digit
            "ES0031406912345678AB1F-01", "ES0031406912345678AB1F.01",
            // A letter outside ASCII, also one that upper-cases into two ASCII letters
            "ES0031406912345678AB1FÑ01", "ES0031406912345678AB1Fß01",
            // A digit outside ASCII
            "ES0031406912345678AB1F٠01"
    })
    @DisplayName("PLT-004 AC5 a CIL that is not exactly 25 ASCII alphanumeric characters once normalised is refused")
    void aCilThatIsNotOneIsRefused(String raw) {
        assertThrows(PlantCilInvalidException.class, () -> PlantCil.normalise(raw));
    }
}
