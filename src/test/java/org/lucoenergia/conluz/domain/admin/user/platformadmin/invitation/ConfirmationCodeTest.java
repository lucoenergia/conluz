package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationCodeTest {

    @Test
    @DisplayName("ACC-015 a code is 8 characters of the Crockford base32 alphabet, without I, L, O or U")
    void aCodeIsEightUnambiguousCharacters() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(ConfirmationCode.generate().value()).matches("[0-9A-HJKMNP-TV-Z]{8}");
        }
    }

    @Test
    @DisplayName("ACC-015 every symbol of the alphabet is used, and codes do not repeat")
    void codesUseTheWholeAlphabet_andDoNotRepeat() {
        Set<Character> symbols = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            String code = ConfirmationCode.generate().value();
            codes.add(code);
            code.chars().forEach(c -> symbols.add((char) c));
        }
        assertThat(symbols).hasSize(ConfirmationCode.ALPHABET.length());
        assertThat(codes).hasSize(2_000);
    }

    @Test
    @DisplayName("ACC-015 a code never shows in its string form")
    void aCodeIsRedacted() {
        ConfirmationCode code = ConfirmationCode.generate();
        assertThat(code.toString()).doesNotContain(code.value());
    }

    @Test
    @DisplayName("ACC-016 a typed code is read in any case, without spaces or hyphens, with O, I and L as digits")
    void aTypedCodeIsNormalised() {
        assertThat(ConfirmationCode.normalize(" 7k3m-q9xd ")).isEqualTo("7K3MQ9XD");
        assertThat(ConfirmationCode.normalize("O1IL")).isEqualTo("0111");
        assertThat(ConfirmationCode.normalize(null)).isNull();
        assertThat(ConfirmationCode.normalize(" - ")).isNull();
    }
}
