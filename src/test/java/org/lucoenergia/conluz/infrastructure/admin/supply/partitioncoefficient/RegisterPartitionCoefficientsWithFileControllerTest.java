package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RegisterPartitionCoefficientsWithFileControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies/partition-coefficients/import";
    private static final String FIXTURE = "fixtures/supplies/partition_coefficients.txt";
    private static final String TXT_CONTENT_TYPE = "text/plain";
    private static final String EFFECTIVE_AT = "2024-01-01T00:00:00Z";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyService createSupplyService;

    @Test
    void importsCoefficientsSuccessfully() throws Exception {
        String authHeader = loginAsDefaultAdmin();
        createSupplyWithCode("ES0031300806333001KE0F");
        createSupplyWithCode("ES0031300326337001WS0F");

        MockMultipartFile file = loadFixture(FIXTURE, TXT_CONTENT_TYPE);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created", hasSize(2)))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[*].item", hasItem("ES0031300UNKNOWNCUPS00")));
    }

    @Test
    void handlesUnknownCupsAsError() throws Exception {
        String authHeader = loginAsDefaultAdmin();
        createSupplyWithCode("ES0031300806333001KE0F");

        MockMultipartFile file = loadFixture(FIXTURE, TXT_CONTENT_TYPE);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", hasSize(1)))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void returnsBadRequestForWrongContentType() throws Exception {
        String authHeader = loginAsDefaultAdmin();
        MockMultipartFile file = loadFixture(FIXTURE, "application/octet-stream");

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void returnsBadRequestForWrongExtension() throws Exception {
        String authHeader = loginAsDefaultAdmin();
        ClassPathResource resource = new ClassPathResource(FIXTURE);
        MockMultipartFile file = new MockMultipartFile(
                "file", "partition_coefficients.csv", TXT_CONTENT_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post(URL)
                        .param("effectiveAt", EFFECTIVE_AT))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void returnsForbiddenForNonAdminRole() throws Exception {
        String authHeader = loginAsPartner();
        MockMultipartFile file = loadFixture(FIXTURE, TXT_CONTENT_TYPE);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }

    private Supply createSupplyWithCode(String code) {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random(user).withCode(code).build();
        return createSupplyService.create(supply, UserId.of(user.getId()));
    }

    private MockMultipartFile loadFixture(String classPathLocation, String contentType) throws Exception {
        ClassPathResource resource = new ClassPathResource(classPathLocation);
        return new MockMultipartFile(
                "file",
                resource.getFilename(),
                contentType,
                Files.readAllBytes(resource.getFile().toPath()));
    }
}
