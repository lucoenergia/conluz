package org.lucoenergia.conluz.infrastructure.admin.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The operator contact as an operator would configure it, through the environment variables: each value surrounded
 * by spaces ({@code \ } keeps a leading one in this format), and the schedule left blank.
 */
@TestPropertySource(properties = {
        "CONLUZ_OPERATOR_CONTACT_NAME=\\  Lucoenergia  ",
        "CONLUZ_OPERATOR_CONTACT_EMAIL=\\  contact@example.org  ",
        "CONLUZ_OPERATOR_CONTACT_PHONE=\\  +34 600 000 000  ",
        "CONLUZ_OPERATOR_CONTACT_SCHEDULE=\\  ",
        "CONLUZ_OPERATOR_CONTACT_NOTES=\\  Write to us if you cannot sign in.  "
})
class GetOperatorContactFullyConfiguredControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("CTC-004 the configured values are returned trimmed, without authentication")
    void theConfiguredValuesAreReturnedTrimmed() throws Exception {
        mockMvc.perform(get("/api/v1/operator-contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.name").value("Lucoenergia"))
                .andExpect(jsonPath("$.contact.email").value("contact@example.org"))
                .andExpect(jsonPath("$.contact.phone").value("+34 600 000 000"))
                .andExpect(jsonPath("$.contact.notes").value("Write to us if you cannot sign in."));
    }

    @Test
    @DisplayName("CTC-004 a blank optional variable is returned as null")
    void aBlankOptionalVariableIsReturnedAsNull() throws Exception {
        mockMvc.perform(get("/api/v1/operator-contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact", hasKey("schedule")))
                .andExpect(jsonPath("$.contact.schedule").value(nullValue()));
    }
}
