package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetAllSharingAgreementsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/sharing-agreements";

    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    @Test
    void testGetAllSharingAgreementsEmpty() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.active").value(0))
                .andExpect(jsonPath("$.previous").value(0))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void testGetAllSharingAgreementsWithData() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        SharingAgreementEntity active = new SharingAgreementEntity();
        active.setId(UUID.randomUUID());
        active.setStartDate(LocalDate.now().minusMonths(1));
        sharingAgreementRepository.save(active);

        SharingAgreementEntity previous = new SharingAgreementEntity();
        previous.setId(UUID.randomUUID());
        previous.setStartDate(LocalDate.now().minusYears(2));
        previous.setEndDate(LocalDate.now().minusYears(1));
        sharingAgreementRepository.save(previous);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.previous").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void testWithoutToken() throws Exception {
        mockMvc.perform(get(URL))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {
        String authHeader = loginAsPartner();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}
