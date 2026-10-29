package org.lucoenergia.conluz.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SupplyCodeTest {

    private static final String CUPS = "ES0031300325733001FH0F";
    private static final String SHORT_CUPS = "ES0031300325733001FH";

    @Test
    @DisplayName("a 22-character CUPS is its own full length")
    void toFullLength_keepsAFullCups() {
        assertThat(SupplyCode.toFullLength(CUPS)).contains(CUPS);
    }

    @Test
    @DisplayName("a 20-character CUPS is completed with 0F, and completing it again changes nothing")
    void toFullLength_completesAShortCups() {
        assertThat(SupplyCode.toFullLength(SHORT_CUPS)).contains(SHORT_CUPS + "0F");
        assertThat(SupplyCode.toFullLength(SHORT_CUPS + "0F")).contains(SHORT_CUPS + "0F");
    }

    @Test
    @DisplayName("the case is kept")
    void toFullLength_keepsTheCase() {
        assertThat(SupplyCode.toFullLength("es0031300325733001fh")).contains("es0031300325733001fh0F");
    }

    @Test
    @DisplayName("any other length, whitespace anywhere, null and empty cannot be a CUPS")
    void toFullLength_refusesAnythingElse() {
        assertThat(SupplyCode.toFullLength(CUPS.substring(0, 19))).isEmpty();
        assertThat(SupplyCode.toFullLength(CUPS.substring(0, 21))).isEmpty();
        assertThat(SupplyCode.toFullLength(CUPS + "X")).isEmpty();
        assertThat(SupplyCode.toFullLength("ES00313003257330 1FH0F")).isEmpty();
        assertThat(SupplyCode.toFullLength(" " + SHORT_CUPS.substring(1))).isEmpty();
        assertThat(SupplyCode.toFullLength(null)).isEqualTo(Optional.empty());
        assertThat(SupplyCode.toFullLength("")).isEmpty();
    }

    @Test
    @DisplayName("SUP-004 a code written to a supply is upper-cased and accepted with 20 or 22 characters")
    void ofCups_upperCasesAndAccepts() {
        assertThat(SupplyCode.ofCups("es0031300325733001fh")).contains(SupplyCode.of(SHORT_CUPS));
        assertThat(SupplyCode.ofCups("es0031300325733001fh0f")).contains(SupplyCode.of(CUPS));
        assertThat(SupplyCode.ofCups(CUPS)).contains(SupplyCode.of(CUPS));
    }

    @Test
    @DisplayName("SUP-004 a code with whitespace, of another length, null or empty is not a CUPS")
    void ofCups_refusesAnythingElse() {
        assertThat(SupplyCode.ofCups("es0031300325733001f")).isEmpty();
        assertThat(SupplyCode.ofCups("ES0031300325733001FH0FX")).isEmpty();
        assertThat(SupplyCode.ofCups("ES00313003257330 1FH0F")).isEmpty();
        assertThat(SupplyCode.ofCups(SHORT_CUPS + " ")).isEmpty();
        assertThat(SupplyCode.ofCups(null)).isEmpty();
        assertThat(SupplyCode.ofCups("")).isEmpty();
    }

    @Test
    @DisplayName("SUP-005 a CUPS ending in 0F is the same as its 20 first characters, in both directions")
    void sameCupsForms_includeBothLengths() {
        assertThat(SupplyCode.of(SHORT_CUPS).sameCupsForms()).containsExactly(CUPS, SHORT_CUPS);
        assertThat(SupplyCode.of(CUPS).sameCupsForms()).containsExactly(CUPS, SHORT_CUPS);
    }

    @Test
    @DisplayName("SUP-005 a 22-character CUPS not ending in 0F has no 20-character form")
    void sameCupsForms_ofAFullCupsWithAnotherSuffix() {
        assertThat(SupplyCode.of(SHORT_CUPS + "1F").sameCupsForms()).containsExactly(SHORT_CUPS + "1F");
    }

    @Test
    @DisplayName("SUP-005 the forms are upper-cased")
    void sameCupsForms_areUpperCase() {
        assertThat(SupplyCode.of("es0031300325733001fh").sameCupsForms()).containsExactly(CUPS, SHORT_CUPS);
    }
}
