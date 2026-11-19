package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PreviewCoefficientDatesControllerTest extends AbstractCoefficientDatesControllerTest {

    @Override
    protected String operation() {
        return "preview-dates";
    }

    @Test
    @DisplayName("SHA-014 the preview writes nothing")
    void writesNothing() throws Exception {
        List<Period> before = rows();

        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), b.id(), firstActivationBody())
                .andExpect(status().isOk());

        assertThat(rows()).isEqualTo(before);
    }
}
