package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import com.fasterxml.jackson.databind.JsonNode;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.BaseInvitationTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inviting and managing a community's admins as the platform admin (#395), against the real database and an
 * embedded SMTP server. Shares its context with the other invitation tests.
 */
abstract class BaseCommunityAdminTest extends BaseInvitationTest {

    /**
     * Every property of an admin in the list, and nothing more.
     */
    protected static final Set<String> ADMIN_FIELDS = Set.of("user", "personalId", "fullName", "contactEmail",
            "phoneNumber", "acceptedAt", "enabled", "invitedAt", "invitationExpiresAt");

    /**
     * A community admin invited by the platform admin, and the link they were sent.
     */
    protected record InvitedAdmin(Community community, String platformAdmin, UUID userId, String personalId,
                                  String contactEmail, String token) {
    }

    // --- Fixtures ---

    protected InvitedAdmin invitedAdmin() throws Exception {
        return invitedAdminOf(newCommunity(), loginAsPlatformAdmin(), newAdminRecord());
    }

    protected InvitedAdmin invitedAdminOf(Community community, String platformAdmin, Map<String, Object> body)
            throws Exception {
        String response = inviteAdmin(platformAdmin, community.getId(), body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(response).get("user").get("id").asText());
        String token = tokenFrom(emailTo(awaitNextEmailsHandedOver(), (String) body.get("contactEmail")));
        return new InvitedAdmin(community, platformAdmin, userId, (String) body.get("personalId"),
                (String) body.get("contactEmail"), token);
    }

    // --- Requests ---

    protected ResultActions admins(String bearer, UUID communityId) throws Exception {
        return mockMvc.perform(get("/api/v1/communities/{communityId}/admins", communityId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    protected JsonNode adminList(String bearer, UUID communityId) throws Exception {
        return objectMapper.readTree(admins(bearer, communityId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    protected ResultActions resendAdmin(String bearer, UUID communityId, UUID userId) throws Exception {
        return adminAction(bearer, communityId, userId, "invitation/resend");
    }

    protected ResultActions revokeAdmin(String bearer, UUID communityId, UUID userId) throws Exception {
        return adminAction(bearer, communityId, userId, "invitation/revoke");
    }

    protected ResultActions enableAdmin(String bearer, UUID communityId, UUID userId) throws Exception {
        return adminAction(bearer, communityId, userId, "enable");
    }

    protected ResultActions disableAdmin(String bearer, UUID communityId, UUID userId) throws Exception {
        return adminAction(bearer, communityId, userId, "disable");
    }

    private ResultActions adminAction(String bearer, UUID communityId, UUID userId, String action)
            throws Exception {
        return mockMvc.perform(post("/api/v1/communities/{communityId}/admins/{userId}/" + action, communityId,
                userId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    // --- Reading responses ---

    protected static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }

    /**
     * The entry of the list for the user, failing if there is none.
     */
    protected static JsonNode entryOf(JsonNode list, UUID userId) {
        for (JsonNode entry : list) {
            if (userId.toString().equals(entry.get("user").get("id").asText())) {
                return entry;
            }
        }
        throw new AssertionError("The list has no admin " + userId);
    }

    protected static boolean lists(JsonNode list, UUID userId) {
        for (JsonNode entry : list) {
            if (userId.toString().equals(entry.get("user").get("id").asText())) {
                return true;
            }
        }
        return false;
    }
}
