package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ImportUsersControllerTest extends BaseControllerTest {

    private final static String URL = "/api/v1/users/import";

    @Test
    void testMinimumBody() throws Exception {

        ClassPathResource resource = new ClassPathResource("users.csv");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void testWithWrongContentType() throws Exception {

        ClassPathResource resource = new ClassPathResource("users.csv");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
