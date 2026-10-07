package org.lucoenergia.conluz.domain.shared.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    private static final EmailCategory CATEGORY = new EmailCategory("PASSWORD_RESET");

    @Test
    void printsItsCategoryOnly() {
        Email email = new Email("someone@example.org", "Asunto", "Cuerpo con token", CATEGORY);

        assertThat(email).hasToString("Email{category=PASSWORD_RESET}");
    }

    @Test
    void requiresEveryPart() {
        assertThatThrownBy(() -> new Email(" ", "Asunto", "Cuerpo", CATEGORY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("someone@example.org", null, "Cuerpo", CATEGORY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("someone@example.org", "Asunto", "", CATEGORY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("someone@example.org", "Asunto", "Cuerpo", null))
                .isInstanceOf(NullPointerException.class);
    }
}
