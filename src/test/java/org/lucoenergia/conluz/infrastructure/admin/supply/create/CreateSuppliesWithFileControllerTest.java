package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.util.Random;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateSuppliesWithFileControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    private final static String URL = "/api/v1/supplies/import";
    public static final String SUPPLIES_CSV = "fixtures/supplies/supplies.csv";
    public static final String SUPPLIES_BAD_FORMAT_CSV = "fixtures/supplies/supplies_bad_format.csv";
    public static final String EMPTY_CSV = "fixtures/empty.csv";
    public static final String SUPPLIES_MALFORMED_CSV = "fixtures/supplies/supplies_malformed.csv";
    public static final String MULTIPART_FILE_NAME = "file";
    public static final String TEXT_CSV_MEDIA_TYPE = "text/csv";

    @Test
    void testMinimumBody() throws Exception {

        String userPersonalId = "987654321S";
        User user = UserMother.randomUserWithId(UUID.fromString("e7ab39cd-9250-40a9-b829-f11f65aae27d"));
        user.setPersonalId(userPersonalId);
        createUserRepository.create(user);

        String supplyCode = "ES002100823465";
        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode(supplyCode)
                .withAddress(RandomStringUtils.random(20, true, true))
                .withPartitionCoefficient(new Random().nextFloat())
                .withEnabled(true)
                .withUser(user)
                .build();
        createSupplyRepository.create(supply, UserId.of(user.getId()));

        ClassPathResource resource = new ClassPathResource(SUPPLIES_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created").isNotEmpty())
                .andExpect(jsonPath("$.created", hasSize(1)))
                .andExpect(jsonPath("$.created", hasItem("ES004567891234")))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].errorMessage", hasItem("El punto de suministro con CUPS 'ES002100823465' ya existe.")))
                .andExpect(jsonPath("$.errors[*].item", hasItem("ES007890123456")))
                .andExpect(jsonPath("$.errors[*].errorMessage", hasItem("El usuario con identificador '456123789D' no ha sido encontrado. Revise que el identificador sea correcto.")))
                .andExpect(jsonPath("$.errors[*].errorMessage", not(hasItem("No ha sido posible crear el punto de suministro con los datos proporcionados."))));
    }

    @Test
    void testWithWrongFormat() throws Exception {

        ClassPathResource resource = new ClassPathResource(SUPPLIES_BAD_FORMAT_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
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
    void testWithWrongContentType() throws Exception {

        ClassPathResource resource = new ClassPathResource(SUPPLIES_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
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

        ClassPathResource resource = new ClassPathResource(SUPPLIES_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
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
                MULTIPART_FILE_NAME,
                "users.txt",
                TEXT_CSV_MEDIA_TYPE,
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

        ClassPathResource resource = new ClassPathResource(EMPTY_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
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

        ClassPathResource resource = new ClassPathResource(SUPPLIES_MALFORMED_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
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
