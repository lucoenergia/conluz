package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateSharingAgreementControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/sharing-agreements";

    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    @Test
    void testCreateSharingAgreement() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(6);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s"
                }
                """, startDate, endDate);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(endDate.toString()))
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.supplyCount").value(0));

        Assertions.assertEquals(1, sharingAgreementRepository.count());
    }

    @Test
    void testCreateSharingAgreementWithNotes() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(6);
        String notes = "Test notes for the agreement";

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s",
                  "notes": "%s"
                }
                """, startDate, endDate, notes);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(endDate.toString()))
                .andExpect(jsonPath("$.notes").value(notes));

        Assertions.assertEquals(1, sharingAgreementRepository.count());
    }

    @Test
    void testCreateClosesActiveAgreement() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        LocalDate activeStartDate = LocalDate.of(2024, 6, 1);
        SharingAgreementEntity activeEntity = new SharingAgreementEntity();
        activeEntity.setId(UUID.randomUUID());
        activeEntity.setStartDate(activeStartDate);
        activeEntity.setEndDate(null);
        sharingAgreementRepository.save(activeEntity);

        LocalDate newStartDate = LocalDate.of(2025, 1, 1);
        LocalDate newEndDate = LocalDate.of(2025, 12, 31);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s"
                }
                """, newStartDate, newEndDate);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.startDate").value(newStartDate.toString()))
                .andExpect(jsonPath("$.endDate").value(newEndDate.toString()));

        Assertions.assertEquals(2, sharingAgreementRepository.count());

        SharingAgreementEntity closedActive = sharingAgreementRepository.findById(activeEntity.getId()).orElseThrow();
        Assertions.assertEquals(LocalDate.of(2024, 12, 31), closedActive.getEndDate());
    }

    @Test
    void testCreateOverlappingWithActiveAgreementShouldFail() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        SharingAgreementEntity activeEntity = new SharingAgreementEntity();
        activeEntity.setId(UUID.randomUUID());
        activeEntity.setStartDate(LocalDate.of(2025, 6, 1));
        activeEntity.setEndDate(null);
        sharingAgreementRepository.save(activeEntity);

        LocalDate newStartDate = LocalDate.of(2025, 1, 1);
        LocalDate newEndDate = LocalDate.of(2025, 12, 31);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s"
                }
                """, newStartDate, newEndDate);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        Assertions.assertEquals(1, sharingAgreementRepository.count());
    }

    @Test
    void testCreateOverlappingWithHistoricalAgreementShouldFail() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        SharingAgreementEntity historicalEntity = new SharingAgreementEntity();
        historicalEntity.setId(UUID.randomUUID());
        historicalEntity.setStartDate(LocalDate.of(2024, 1, 1));
        historicalEntity.setEndDate(LocalDate.of(2024, 6, 30));
        sharingAgreementRepository.save(historicalEntity);

        LocalDate newStartDate = LocalDate.of(2024, 3, 1);
        LocalDate newEndDate = LocalDate.of(2024, 9, 30);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s"
                }
                """, newStartDate, newEndDate);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        Assertions.assertEquals(1, sharingAgreementRepository.count());
    }

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
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

        mockMvc.perform(post(URL)
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
    void testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
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
    void testWithoutToken() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(6);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s"
                }
                """, startDate, endDate);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}
