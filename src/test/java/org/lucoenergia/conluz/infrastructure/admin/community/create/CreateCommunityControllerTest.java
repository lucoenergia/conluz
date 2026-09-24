package org.lucoenergia.conluz.infrastructure.admin.community.create;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code code} and {@code legalId} are unique across communities and the repository has always
 * checked both, but {@code CommunityAlreadyExistsException} was never mapped, so a duplicate left
 * the application as a 500 with no {@code RestError} body.
 *
 * <p>It is now a 409, matching the duplicate-membership mapping: the body is well-formed and the
 * caller is authorized, and what stops the request is that the value is already taken.
 */
@Transactional
class CreateCommunityControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities";

    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    @Test
    void reusingTheCodeOfAnotherCommunityIsReportedAsAConflict() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity existing = persistCommunity();

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A different name", existing.getCode(), uniqueLegalId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_ALREADY_EXISTS"))
                // Which of the two unique fields collided, so a client can mark the right input.
                .andExpect(jsonPath("$.errors[0].params.field").value("code"))
                .andExpect(jsonPath("$.errors[0].params.value").value(existing.getCode()));
    }

    /**
     * The legal id is the second unique column and reports its own field name, so a client is not
     * left highlighting the code input for a legal-id collision.
     */
    @Test
    void reusingTheLegalIdOfAnotherCommunityIsReportedAsAConflict() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity existing = persistCommunity();

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A different name", uniqueCode(), existing.getLegalId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.errors[0].params.field").value("legalId"))
                .andExpect(jsonPath("$.errors[0].params.value").value(existing.getLegalId()));
    }

    @Test
    void theConflictLeavesTheExistingCommunityUntouched() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity existing = persistCommunity();
        long countBefore = communityJpaRepository.count();

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A different name", existing.getCode(), uniqueLegalId())))
                .andExpect(status().isConflict());

        assertEquals(countBefore, communityJpaRepository.count(), "no community should have been created");
        assertEquals(existing.getName(),
                communityJpaRepository.findById(existing.getId()).orElseThrow().getName(),
                "the existing community must not have been modified");
    }

    /**
     * The happy path, so the conflict tests are known to be failing on the duplicate rather than on
     * the request being malformed or the caller being rejected.
     */
    @Test
    void creatingACommunityWithUnusedValuesSucceeds() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        String code = uniqueCode();

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A brand new community", code, uniqueLegalId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));

        assertTrue(communityJpaRepository.existsByCode(code));
    }

    @Test
    void theContractDeclaresTheConflictForThisOperation() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responses = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("paths").path(PATH).path("post").path("responses");

        assertTrue(!responses.path("409").isMissingNode(),
                () -> "the POST operation declares no 409: " + fieldNames(responses));
    }

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    private static String body(String name, String code, String legalId) {
        return "{\"name\": \"" + name + "\", \"code\": \"" + code + "\", \"legalId\": \"" + legalId + "\"}";
    }

    private static String uniqueCode() {
        return "CODE-" + UUID.randomUUID();
    }

    private static String uniqueLegalId() {
        return "LEGAL-" + UUID.randomUUID();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
