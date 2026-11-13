package org.lucoenergia.conluz.infrastructure.shared.web.error;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What every request refused for an address longer than its maximum is answered with (#574).
 */
public final class FieldTooLongTestSupport {

    private FieldTooLongTestSupport() {
    }

    /**
     * An address of exactly {@code length} characters that no other value of a test holds, so that a response or a
     * log line repeating it can be told apart.
     */
    public static String address(int length) {
        String prefix = "ADDRESS-" + Integer.toHexString(System.identityHashCode(new Object())) + "-";
        return prefix + "x".repeat(length - prefix.length());
    }

    /**
     * A request that is refused with one FIELD_TOO_LONG for {@code field}, with neither the response nor a log line
     * repeating {@code value}.
     */
    public static void assertFieldTooLong(RequestCall request, String field, String value) throws Exception {
        try (LogCapture logs = LogCapture.ofRoot()) {
            String body = request.perform()
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.length()").value(1))
                    .andExpect(jsonPath("$.errors[0].code").value("FIELD_TOO_LONG"))
                    .andExpect(jsonPath("$.errors[0].params.field").value(field))
                    .andExpect(jsonPath("$.errors[0].params.maxLength").value("250"))
                    .andExpect(jsonPath("$.errors[0].params.n").value(String.valueOf(value.length())))
                    .andExpect(jsonPath("$.errors[0].params.length()").value(3))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain(value);
            assertThat(logs.all()).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(line -> assertThat(line).doesNotContain(value));
        }
    }

    @FunctionalInterface
    public interface RequestCall {
        ResultActions perform() throws Exception;
    }
}
