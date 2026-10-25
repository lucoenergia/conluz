package org.lucoenergia.conluz.infrastructure.shared.web.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An upload over the multipart limits, on a real embedded Tomcat: under MockMvc a multipart request arrives already
 * parsed, so neither Tomcat's size check nor Spring's multipart resolution runs (#479).
 *
 * <p>Nothing runs in a test transaction, since the server handles each request on its own thread; what a test creates
 * is deleted afterwards.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PayloadTooLargeControllerTest extends BaseIntegrationTest {

    private static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final String BOUNDARY = "conluz-test-boundary";

    @LocalServerPort
    private int port;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private Community community;
    private User admin;

    @AfterEach
    void deleteWhatWasCreated() {
        if (community != null) {
            jdbcTemplate.update("DELETE FROM community_memberships WHERE community_id = ?", community.getId());
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", community.getId());
        }
        if (admin != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", admin.getId());
        }
    }

    @Test
    void aFileJustOverTheLimit_isAnswered413_withTheLimitInBytes() throws Exception {
        HttpResponse<String> response = importMembers(MAX_FILE_SIZE_BYTES + 1024);

        assertPayloadTooLarge(response);
    }

    @Test
    void twentyMegabyteUpload_isAnswered413_althoughItIsRefusedBeforeAnyOfItIsRead() throws Exception {
        HttpResponse<String> response = importMembers(20 * 1024 * 1024);

        assertPayloadTooLarge(response);
    }

    private void assertPayloadTooLarge(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(413);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(413);
        assertThat(body.get("traceId").asText()).isNotBlank();
        JsonNode error = body.get("errors").get(0);
        assertThat(error.get("code").asText()).isEqualTo("REQUEST_PAYLOAD_TOO_LARGE");
        assertThat(error.get("params").get("maxFileSizeBytes").asText()).isEqualTo(String.valueOf(MAX_FILE_SIZE_BYTES));
        assertThat(error.get("message").asText()).contains("10");
    }

    /**
     * Uploads a member file of {@code size} bytes as the admin of a new community.
     */
    private HttpResponse<String> importMembers(int size) throws Exception {
        community = createCommunityRepository.create(CommunityMother.random().build());
        admin = UserMother.randomUser();
        admin.enable();
        String password = admin.getPassword();
        createUserRepository.create(admin);
        createMembershipRepository.create(community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        String token = login(admin.getPersonalId(), password);

        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/communities/" + community.getId()
                        + "/memberships/import"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartFile(size)))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String login(String personalId, String password) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        Map.of("username", personalId, "password", password))))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body()).get("token").asText();
    }

    private static byte[] multipartFile(int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 'a');
        ByteArrayOutputStream body = new ByteArrayOutputStream(size + 512);
        body.writeBytes(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"members-" + UUID.randomUUID() + ".csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
