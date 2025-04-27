package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyPartitionRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.*;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateSuppliesPartitionsWithFileControllerTest extends BaseControllerTest {

    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    @Autowired
    private CreateSupplyPartitionRepository createSupplyPartitionRepository;

    @Autowired
    private SupplyRepository supplyRepository;

    @Autowired
    private UserRepository userRepository;

    // No need to mock the service for these simplified tests

    private static final String URL = "/api/v1/supplies/partitions/import";
    public static final String SUPPLY_PARTITIONS_CSV = "fixtures/supplies/supply_partitions.csv";
    public static final String SUPPLY_PARTITIONS_MALFORMED_CSV = "fixtures/supplies/supply_partitions_malformed.csv";
    public static final String EMPTY_CSV = "fixtures/empty.csv";
    public static final String MULTIPART_FILE_NAME = "file";
    public static final String TEXT_CSV_MEDIA_TYPE = "text/csv";

    @Test
    void testMinimumBody() throws Exception {
        // Create a user
        UserEntity user = UserMother.randomUserEntity();
        user = userRepository.save(user);

        // Create a supply with the specific code
        SupplyEntity supplyEntity = SupplyEntityMother.random(user);
        supplyEntity.setCode("ES015678901234");
        supplyRepository.save(supplyEntity);

        // Create a sharing agreement
        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);
        UUID sharingAgreementId = sharingAgreement.getId();

        ClassPathResource resource = new ClassPathResource(SUPPLY_PARTITIONS_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLY_PARTITIONS_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("sharingAgreementId", sharingAgreementId.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created").isNotEmpty())
                .andExpect(jsonPath("$.created", hasSize(1)))
                .andExpect(jsonPath("$.created[*].supply.code", hasItem("ES015678901234")))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].item.code", hasItem("ES004567891234")))
                .andExpect(jsonPath("$.errors[*].errorMessage", hasItem("No ha sido posible crear el punto de suministro con los datos proporcionados.")));
    }

    @Test
    void testWithMalformedCsvFile() throws Exception {
        // Create a sharing agreement
        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);
        UUID sharingAgreementId = sharingAgreement.getId();

        ClassPathResource resource = new ClassPathResource(SUPPLY_PARTITIONS_MALFORMED_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLY_PARTITIONS_MALFORMED_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("sharingAgreementId", sharingAgreementId.toString())
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
        // Create a sharing agreement
        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);
        UUID sharingAgreementId = sharingAgreement.getId();

        // No need to mock the create method since the CSV file is empty

        ClassPathResource resource = new ClassPathResource(EMPTY_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLY_PARTITIONS_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("sharingAgreementId", sharingAgreementId.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created").isEmpty())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void testWithWrongContentType() throws Exception {
        // Create a sharing agreement
        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);
        UUID sharingAgreementId = sharingAgreement.getId();

        ClassPathResource resource = new ClassPathResource(SUPPLY_PARTITIONS_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLY_PARTITIONS_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("sharingAgreementId", sharingAgreementId.toString())
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
        // Create a sharing agreement
        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);
        UUID sharingAgreementId = sharingAgreement.getId();

        ClassPathResource resource = new ClassPathResource(SUPPLY_PARTITIONS_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLY_PARTITIONS_CSV,
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("sharingAgreementId", sharingAgreementId.toString())
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
        // Create a sharing agreement
        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);
        UUID sharingAgreementId = sharingAgreement.getId();

        ClassPathResource resource = new ClassPathResource("application-test.properties");

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                "supply_partitions.txt",
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("sharingAgreementId", sharingAgreementId.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutFile() throws Exception {
        // Create a sharing agreement
        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);
        UUID sharingAgreementId = sharingAgreement.getId();

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
                        .param("sharingAgreementId", sharingAgreementId.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutSharingAgreementId() throws Exception {
        ClassPathResource resource = new ClassPathResource(SUPPLY_PARTITIONS_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLY_PARTITIONS_CSV,
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
    void testWithNonExistentSharingAgreementId() throws Exception {
        UUID nonExistentSharingAgreementId = UUID.randomUUID();

        ClassPathResource resource = new ClassPathResource(SUPPLY_PARTITIONS_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLY_PARTITIONS_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("sharingAgreementId", nonExistentSharingAgreementId.toString())
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
        UUID sharingAgreementId = UUID.randomUUID();

        mockMvc.perform(post(URL)
                        .param("sharingAgreementId", sharingAgreementId.toString()))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
