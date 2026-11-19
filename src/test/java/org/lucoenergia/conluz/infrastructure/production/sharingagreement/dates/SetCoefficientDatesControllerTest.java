package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SetCoefficientDatesControllerTest extends AbstractCoefficientDatesControllerTest {

    @Override
    protected String operation() {
        return "set-dates";
    }

    @Test
    void writesTheDates() throws Exception {
        List<Period> before = rows();

        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), b.id(), firstActivationBody())
                .andExpect(status().isOk());

        assertThat(rows()).isNotEqualTo(before);
        assertThat(rows()).filteredOn(row -> row.id().equals(b.of(x)))
                .extracting(Period::validFrom).doesNotContainNull();
    }
}
