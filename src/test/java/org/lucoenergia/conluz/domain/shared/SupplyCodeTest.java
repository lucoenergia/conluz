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
}
