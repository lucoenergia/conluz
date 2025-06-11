package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UpdateSharingAgreementControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/sharing-agreements";

    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    @Test
    void testUpdateSharingAgreement() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        // Create a sharing agreement
        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(UUID.randomUUID());
        entity.setStartDate(LocalDate.of(2023, 1, 1));
        entity.setEndDate(LocalDate.of(2023, 12, 31));
        sharingAgreementRepository.save(entity);

        // New dates for update
        LocalDate newStartDate = LocalDate.of(2023, 2, 1);
        LocalDate newEndDate = LocalDate.of(2023, 11, 30);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s"
                }
                """, newStartDate, newEndDate);

        mockMvc.perform(put(URL + "/" + entity.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entity.getId().toString()))
                .andExpect(jsonPath("$.startDate").value("2023-02-01"))
                .andExpect(jsonPath("$.endDate").value("2023-11-30"));
    }

    @Test
    void testUpdateNonExistentSharingAgreement() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        UUID nonExistentId = UUID.randomUUID();

        String body = """
                {
                  "startDate": "2023-02-01",
                  "endDate": "2023-11-30"
                }
                """;

        mockMvc.perform(put(URL + "/" + nonExistentId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {
        String authHeader = loginAsDefaultAdmin();

        UUID id = UUID.randomUUID();

        mockMvc.perform(put(URL + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithMissingRequiredFields() {
        return List.of(
                """
                {
                  "endDate": "2023-12-31"
                }
                """,
                """
                {
                }
                """
        );
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {
        String authHeader = loginAsDefaultAdmin();

        UUID id = UUID.randomUUID();

        mockMvc.perform(put(URL + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithInvalidFormatValues() {
        return List.of(
                """
                {
                  "startDate": "invalid-date",
                  "endDate": "2023-12-31"
                }
                """,
                """
                {
                  "startDate": "2023-01-01",
                  "endDate": "invalid-date"
                }
                """,
                """
                {
                  "startDate": "01/01/2023",
                  "endDate": "31/12/2023"
                }
                """
        );
    }

    @Test
    void testWithInvalidId() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        String body = """
                {
                  "startDate": "2023-02-01",
                  "endDate": "2023-11-30"
                }
                """;

        mockMvc.perform(put(URL + "/invalid-id")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        UUID id = UUID.randomUUID();

        mockMvc.perform(put(URL + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutIdInPath() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        String body = """
                {
                  "startDate": "2023-02-01",
                  "endDate": "2023-11-30"
                }
                """;

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {
        UUID id = UUID.randomUUID();

        String body = """
                {
                  "startDate": "2023-02-01",
                  "endDate": "2023-11-30"
                }
                """;

        mockMvc.perform(put(URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}