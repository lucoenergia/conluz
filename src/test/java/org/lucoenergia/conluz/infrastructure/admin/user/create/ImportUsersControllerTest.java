package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isNotEmpty());
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

    @Test
    void testWithWrongFileMimeType() throws Exception {

        ClassPathResource resource = new ClassPathResource("users.csv");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "application/octet-stream",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithWrongFileExtension() throws Exception {

        ClassPathResource resource = new ClassPathResource("application-test.properties");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.txt",
                "text/csv",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithEmptyCsvFile() throws Exception {

        ClassPathResource resource = new ClassPathResource("empty.csv");

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created").isEmpty())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void testWithMalformedCsvFile() throws Exception {

        ClassPathResource resource = new ClassPathResource("users_malformed.csv");

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void
    testWithoutFile() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        mockMvc.perform(post(URL))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
