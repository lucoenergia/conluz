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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ExportSharingAgreementControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/sharing-agreements/%s/supply-partitions/export";

    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    @Test
    void testExportEmptyAgreement() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(UUID.randomUUID());
        entity.setStartDate(LocalDate.now());
        sharingAgreementRepository.save(entity);

        mockMvc.perform(get(String.format(URL_TEMPLATE, entity.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"partitions-" + entity.getId() + ".txt\""));
    }

    @Test
    void testExportNonExistentAgreement() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get(String.format(URL_TEMPLATE, nonExistentId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void testWithoutToken() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get(String.format(URL_TEMPLATE, id)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }
}
