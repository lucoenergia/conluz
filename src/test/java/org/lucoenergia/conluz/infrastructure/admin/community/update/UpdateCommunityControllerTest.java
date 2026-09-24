package org.lucoenergia.conluz.infrastructure.admin.community.update;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Update had the same 500 as create, one endpoint over: it assigned {@code code} and
 * {@code legalId} with no uniqueness check, so a rename onto a value another community already
 * used reached the unique constraint, whose {@code DataIntegrityViolationException} is mapped
 * nowhere.
 *
 * <p>The checks exclude the community being updated, which is the part that has to be right: a
 * naive check would make every update that keeps its own code collide with itself and fail.
 */
@Transactional
class UpdateCommunityControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}";

    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    @Test
    void movingTheCodeOntoAnotherCommunitysCodeIsReportedAsAConflict() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity target = persistCommunity();
        CommunityEntity other = persistCommunity();

        mockMvc.perform(put(PATH, target.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(target.getName(), other.getCode(), uniqueLegalId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.errors[0].params.field").value("code"))
                .andExpect(jsonPath("$.errors[0].params.value").value(other.getCode()));
    }

    @Test
    void movingTheLegalIdOntoAnotherCommunitysLegalIdIsReportedAsAConflict() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity target = persistCommunity();
        CommunityEntity other = persistCommunity();

        mockMvc.perform(put(PATH, target.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(target.getName(), uniqueCode(), other.getLegalId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].params.field").value("legalId"))
                .andExpect(jsonPath("$.errors[0].params.value").value(other.getLegalId()));
    }

    @Test
    void theConflictLeavesBothCommunitiesUntouched() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity target = persistCommunity();
        CommunityEntity other = persistCommunity();

        mockMvc.perform(put(PATH, target.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A new name", other.getCode(), uniqueLegalId())))
                .andExpect(status().isConflict());

        assertEquals(target.getCode(), reload(target.getId()).getCode(),
                "the target community's code must not have changed");
        assertEquals(target.getName(), reload(target.getId()).getName(),
                "the rejected request must not have applied the name either");
        assertEquals(other.getCode(), reload(other.getId()).getCode(),
                "the other community must not have been touched");
    }

    /**
     * The case the exclusion exists for: resubmitting a community's own code and legal id is the
     * ordinary way to edit its name, and a check that did not exclude the row being updated would
     * reject it.
     */
    @Test
    void keepingItsOwnCodeAndLegalIdIsNotAConflict() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity target = persistCommunity();

        mockMvc.perform(put(PATH, target.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A renamed community", target.getCode(), target.getLegalId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("A renamed community"))
                .andExpect(jsonPath("$.code").value(target.getCode()));

        assertEquals("A renamed community", reload(target.getId()).getName());
    }

    @Test
    void movingOntoAnUnusedCodeSucceeds() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity target = persistCommunity();
        // A second community exists, so the check is exercised against a populated table.
        persistCommunity();
        String newCode = uniqueCode();

        mockMvc.perform(put(PATH, target.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(target.getName(), newCode, target.getLegalId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(newCode));

        assertEquals(newCode, reload(target.getId()).getCode());
    }

    @Test
    void theContractDeclaresTheConflictForThisOperation() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responses = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("paths").path("/api/v1/communities/{communityId}").path("put").path("responses");

        assertTrue(!responses.path("409").isMissingNode(),
                () -> "the PUT operation declares no 409: " + fieldNames(responses));
    }

    private CommunityEntity reload(UUID id) {
        return communityJpaRepository.findById(id).orElseThrow();
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
