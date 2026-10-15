package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Listing a community's admins (MEM-021): admin memberships only, each with this community's member record, the
 * personal id from the account, and the state of the membership and its invitation.
 */
class GetCommunityAdminsControllerTest extends BaseCommunityAdminTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // --- AC2 ---

    @Test
    @DisplayName("MEM-021 the list shows exactly the record, the personal id and the state of each admin "
            + "membership, and nothing about the members")
    void theList_showsExactlyTheAdminMemberships() throws Exception {
        Community community = newCommunity();
        String platformAdmin = loginAsPlatformAdmin();
        Map<String, Object> body = newAdminRecord();
        InvitedAdmin invited = invitedAdminOf(community, platformAdmin, body);
        User acceptedAdmin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        User acceptedMember = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Map<String, Object> memberBody = newRecord();
        UUID pendingMember = registered(loginUser(acceptedAdmin), community.getId(), memberBody);

        JsonNode list = adminList(platformAdmin, community.getId());

        assertThat(list).hasSize(2);
        for (JsonNode entry : list) {
            assertThat(fieldNames(entry)).isEqualTo(new TreeSet<>(ADMIN_FIELDS));
            assertThat(fieldNames(entry.get("user"))).containsExactly("id");
        }
        JsonNode pending = entryOf(list, invited.userId());
        assertThat(pending.get("personalId").asText()).isEqualTo(invited.personalId());
        assertThat(pending.get("fullName").asText()).isEqualTo(body.get("fullName"));
        assertThat(pending.get("contactEmail").asText()).isEqualTo(invited.contactEmail());
        assertThat(pending.get("phoneNumber").asText()).isEqualTo(body.get("phoneNumber"));
        assertThat(pending.get("acceptedAt").isNull()).isTrue();
        assertThat(pending.get("enabled").asBoolean()).isTrue();
        assertThat(pending.get("invitedAt").asText()).isEqualTo(clock.instant().toString());
        assertThat(pending.get("invitationExpiresAt").asText())
                .isEqualTo(clock.instant().plus(Duration.ofDays(7)).toString());

        JsonNode accepted = entryOf(list, acceptedAdmin.getId());
        assertThat(accepted.get("personalId").asText()).isEqualTo(acceptedAdmin.getPersonalId());
        assertThat(accepted.get("acceptedAt").isNull()).isFalse();
        assertThat(accepted.get("invitedAt").isNull()).isTrue();
        assertThat(accepted.get("invitationExpiresAt").isNull()).isTrue();

        assertThat(lists(list, acceptedMember.getId())).isFalse();
        assertThat(lists(list, pendingMember)).isFalse();
        String text = list.toString();
        assertThat(text).doesNotContain(acceptedMember.getPersonalId(), (String) memberBody.get("personalId"),
                (String) memberBody.get("contactEmail"));
    }

    @Test
    @DisplayName("MEM-021 a disabled admin membership is listed, as disabled")
    void aDisabledAdmin_isListedAsDisabled() throws Exception {
        InvitedAdmin admin = invitedAdmin();
        disableAdmin(admin.platformAdmin(), admin.community().getId(), admin.userId()).andExpect(status().isOk());

        JsonNode entry = entryOf(adminList(admin.platformAdmin(), admin.community().getId()), admin.userId());

        assertThat(entry.get("enabled").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("MEM-021 a community without admins lists none")
    void aCommunityWithoutAdmins_listsNone() throws Exception {
        Community community = newCommunity();
        memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        assertThat(adminList(loginAsPlatformAdmin(), community.getId())).isEmpty();
    }

    // --- AC3 ---

    @Test
    @DisplayName("MEM-021 a person who administers two communities is listed in each with that community's record "
            + "and the same personal id")
    void anAdminOfTwoCommunities_isListedWithEachCommunitysRecord() throws Exception {
        Community first = newCommunity();
        Community second = newCommunity();
        String platformAdmin = loginAsPlatformAdmin();
        Map<String, Object> firstBody = newAdminRecord();
        firstBody.put("fullName", "Marta Ruiz Sanz");
        firstBody.put("phoneNumber", "611111111");
        InvitedAdmin inFirst = invitedAdminOf(first, platformAdmin, firstBody);
        Map<String, Object> secondBody = newAdminRecord();
        secondBody.put("personalId", inFirst.personalId());
        secondBody.put("fullName", "M. Ruiz");
        secondBody.put("phoneNumber", "622222222");
        InvitedAdmin inSecond = invitedAdminOf(second, platformAdmin, secondBody);
        assertThat(inSecond.userId()).isEqualTo(inFirst.userId());

        JsonNode firstEntry = entryOf(adminList(platformAdmin, first.getId()), inFirst.userId());
        JsonNode secondEntry = entryOf(adminList(platformAdmin, second.getId()), inSecond.userId());

        assertThat(firstEntry.get("fullName").asText()).isEqualTo("Marta Ruiz Sanz");
        assertThat(firstEntry.get("contactEmail").asText()).isEqualTo(firstBody.get("contactEmail"));
        assertThat(firstEntry.get("phoneNumber").asText()).isEqualTo("611111111");
        assertThat(secondEntry.get("fullName").asText()).isEqualTo("M. Ruiz");
        assertThat(secondEntry.get("contactEmail").asText()).isEqualTo(secondBody.get("contactEmail"));
        assertThat(secondEntry.get("phoneNumber").asText()).isEqualTo("622222222");
        assertThat(firstEntry.get("personalId").asText()).isEqualTo(inFirst.personalId());
        assertThat(secondEntry.get("personalId").asText()).isEqualTo(inFirst.personalId());
    }

    // --- AC4 ---

    @Test
    @DisplayName("MEM-021 nothing of a person's member membership elsewhere is seen through the admin list")
    void aMembershipElsewhere_isNotSeen() throws Exception {
        Community administered = newCommunity();
        Community joined = newCommunity();
        String platformAdmin = loginAsPlatformAdmin();
        InvitedAdmin admin = invitedAdminOf(administered, platformAdmin, newAdminRecord());
        Map<String, Object> memberBody = newRecord();
        memberBody.put("personalId", admin.personalId());
        memberBody.put("fullName", "Name Only The Other Community Knows");
        memberBody.put("phoneNumber", "633333333");
        registered(loginAsAdminOf(joined), joined.getId(), memberBody);

        String administeredList = adminList(platformAdmin, administered.getId()).toString();
        JsonNode joinedList = adminList(platformAdmin, joined.getId());

        assertThat(administeredList).doesNotContain("Name Only The Other Community Knows", "633333333",
                (String) memberBody.get("contactEmail"), joined.getId().toString());
        assertThat(lists(joinedList, admin.userId())).isFalse();
        assertThat(joinedList.toString()).doesNotContain("Name Only The Other Community Knows",
                (String) memberBody.get("contactEmail"));
    }

    // --- Query cost ---

    @Test
    @DisplayName("MEM-021 the list reads the invitation expiries in one query, whatever the number of admins")
    void theList_readsTheExpiriesInOneQuery() throws Exception {
        Community community = newCommunity();
        String platformAdmin = loginAsPlatformAdmin();
        invitedAdminOf(community, platformAdmin, newAdminRecord());
        long forOne = statementsForList(platformAdmin, community.getId());

        for (int i = 0; i < 4; i++) {
            invitedAdminOf(community, platformAdmin, newAdminRecord());
        }
        long forFive = statementsForList(platformAdmin, community.getId());

        assertThat(forFive).as("the list must not query once per admin").isEqualTo(forOne);
    }

    // --- AC7 ---

    @Test
    void anUnknownCommunity_isNotFound() throws Exception {
        admins(loginAsPlatformAdmin(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MEM-021 a community admin of the community is forbidden, as for an unknown community")
    void aCommunityAdmin_isForbidden() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);

        admins(bearer, community.getId()).andExpect(status().isForbidden());
        admins(bearer, UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEM-021 a member of the community is forbidden")
    void aMember_isForbidden() throws Exception {
        Community community = newCommunity();

        admins(loginAsMemberOf(community), community.getId()).andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/communities/{communityId}/admins", newCommunity().getId()))
                .andExpect(status().isUnauthorized());
    }

    private long statementsForList(String bearer, UUID communityId) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        adminList(bearer, communityId);
        return statistics.getPrepareStatementCount();
    }
}
