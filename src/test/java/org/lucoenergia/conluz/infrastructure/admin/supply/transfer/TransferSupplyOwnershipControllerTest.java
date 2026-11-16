package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class TransferSupplyOwnershipControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies/%s/ownership/transfer";
    /** 11:00 in Madrid on 2026-03-16. */
    private static final Instant NOW = Instant.parse("2026-03-16T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 16);

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private Community community;
    private User admin;
    private String adminToken;

    @BeforeEach
    void givenACommunityWithAnAdmin() throws Exception {
        travelTo(NOW);
        community = createCommunityRepository.create(CommunityMother.random().build());
        admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        adminToken = loginUser(admin);
    }

    @Test
    @DisplayName("SUP-009 a supply is transferred to a member whose membership is active, and the response "
            + "carries the new owner and their member record")
    void aSupplyIsTransferredToAnActiveMember() throws Exception {
        assertTransferredTo(memberOf(community, CommunityRole.COMMUNITY_MEMBER));
    }

    @Test
    @DisplayName("SUP-009 a supply is transferred to a member whose membership is pending")
    void aSupplyIsTransferredToAPendingMember() throws Exception {
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(membershipJpaRepository, next.getId(), community.getId());

        assertTransferredTo(next);
    }

    @Test
    @DisplayName("SUP-009 a supply is transferred to a member whose membership is disabled")
    void aSupplyIsTransferredToADisabledMember() throws Exception {
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markDisabled(membershipJpaRepository, next.getId(), community.getId());

        assertTransferredTo(next);
    }

    @Test
    @DisplayName("SUP-011 a transfer is recorded with the supply, both owners, the effective date, the history "
            + "included, who made it and when")
    void aTransferIsRecordedWithEveryField() throws Exception {
        User previous = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(previous, community);
        LocalDate effectiveDate = TODAY.minusDays(10);

        transfer(supply, next.getPersonalId(), effectiveDate, adminToken).andExpect(status().isOk());

        List<Map<String, Object>> records = recordsOf(supply);
        assertThat(records).hasSize(1);
        Map<String, Object> record = records.get(0);
        assertThat(record.get("previous_user_id")).isEqualTo(previous.getId());
        assertThat(record.get("new_user_id")).isEqualTo(next.getId());
        assertThat(record.get("effective_date")).isEqualTo(Date.valueOf(effectiveDate));
        assertThat(record.get("history_included")).isEqualTo(true);
        assertThat(record.get("transferred_by")).isEqualTo(admin.getId());
        assertThat(((Timestamp) record.get("transferred_at")).toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("SUP-011 a supply without an owner is transferred, and its record has no previous owner")
    void aSupplyWithoutAnOwnerIsTransferred() throws Exception {
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(memberOf(community, CommunityRole.COMMUNITY_MEMBER), community);
        // No repository creates a supply without an owner: only deletions before #412 left them
        entityManager.flush();
        jdbcTemplate.update("UPDATE supplies SET user_id = NULL WHERE id = ?", supply.getId());
        // The request runs in the test's transaction, whose session still holds the supply with its owner
        entityManager.clear();

        transfer(supply, next.getPersonalId(), TODAY, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(next.getId().toString()));

        List<Map<String, Object>> records = recordsOf(supply);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("previous_user_id")).isNull();
        assertThat(records.get(0).get("new_user_id")).isEqualTo(next.getId());
    }

    @Test
    @DisplayName("SUP-009 a personal ID typed with spaces, dots, a hyphen and a lower-case letter resolves to the "
            + "member")
    void aPersonalIdTypingVariantResolvesToTheMember() throws Exception {
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(memberOf(community, CommunityRole.COMMUNITY_MEMBER), community);
        String id = next.getPersonalId();
        String variant = " " + id.substring(0, 2) + "." + id.substring(2, 5) + "." + id.substring(5, 8) + "-"
                + id.substring(8).toLowerCase() + " ";

        transfer(supply, variant, TODAY, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(next.getId().toString()));
    }

    @Test
    @DisplayName("SUP-009 a personal ID that matches no user and a user who is not a member of the supply's "
            + "community are answered alike with 404 SUPPLY_OWNER_NOT_FOUND, and nothing is written")
    void anUnknownPersonalIdAndANonMemberAreAnsweredAlike() throws Exception {
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);
        Community other = createCommunityRepository.create(CommunityMother.random().build());
        User outsider = memberOf(other, CommunityRole.COMMUNITY_MEMBER);

        JsonNode unknown = refusal(transfer(supply, UserMother.randomPersonalId(), TODAY, adminToken), 404);
        JsonNode nonMember = refusal(transfer(supply, outsider.getPersonalId(), TODAY, adminToken), 404);

        assertThat(unknown.at("/errors/0/code").asText()).isEqualTo("SUPPLY_OWNER_NOT_FOUND");
        assertThat(unknown.at("/errors/0/message").asText()).isNotEmpty();
        assertThat(nonMember).isEqualTo(unknown);
        assertThat(nonMember.toString()).doesNotContain(outsider.getPersonalId());
        assertNothingWritten(supply, owner);
    }

    @Test
    @DisplayName("SUP-009 transferring a supply to its owner is refused with 409 SUPPLY_ALREADY_OWNED_BY_USER, and "
            + "nothing is written")
    void transferringASupplyToItsOwnerIsRefused() throws Exception {
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);

        JsonNode body = refusal(transfer(supply, owner.getPersonalId(), TODAY, adminToken), 409);

        assertThat(body.at("/errors/0/code").asText()).isEqualTo("SUPPLY_ALREADY_OWNED_BY_USER");
        assertThat(body.at("/errors/0/message").asText()).isNotEmpty();
        assertNothingWritten(supply, owner);
    }

    @Test
    @DisplayName("SUP-010 a date after today in the community's time zone is refused with 400 "
            + "SUPPLY_TRANSFER_DATE_IN_FUTURE, and nothing is written")
    void aDateAfterTodayIsRefused() throws Exception {
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        JsonNode body = refusal(transfer(supply, next.getPersonalId(), TODAY.plusDays(1), adminToken), 400);

        assertThat(body.at("/errors/0/code").asText()).isEqualTo("SUPPLY_TRANSFER_DATE_IN_FUTURE");
        assertNothingWritten(supply, owner);
    }

    @Test
    @DisplayName("SUP-010 today is the community's today: just after midnight in Madrid its date is accepted, "
            + "although it is still the day before in UTC")
    void todayIsTheCommunitysToday_afterMidnight() throws Exception {
        travelTo(Instant.parse("2026-03-15T23:30:00Z"));
        Supply supply = supplyOf(memberOf(community, CommunityRole.COMMUNITY_MEMBER), community);
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        transfer(supply, next.getPersonalId(), LocalDate.of(2026, 3, 16), adminToken)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUP-010 today is the community's today: just before midnight in Madrid the next date is "
            + "refused, although it is already that date in a zone further east")
    void todayIsTheCommunitysToday_beforeMidnight() throws Exception {
        travelTo(Instant.parse("2026-03-15T22:30:00Z"));
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        JsonNode body = refusal(transfer(supply, next.getPersonalId(), LocalDate.of(2026, 3, 16), adminToken),
                400);

        assertThat(body.at("/errors/0/code").asText()).isEqualTo("SUPPLY_TRANSFER_DATE_IN_FUTURE");
        assertNothingWritten(supply, owner);
    }

    @Test
    @DisplayName("SUP-010 a date equal to the latest of the supply's previous transfers is refused with 409 "
            + "SUPPLY_TRANSFER_DATE_NOT_AFTER_PREVIOUS, and nothing is written")
    void aDateEqualToThePreviousTransfersIsRefused() throws Exception {
        TransferredSupply given = aSupplyTransferredTwice();

        JsonNode body = refusal(transfer(given.supply(), given.third().getPersonalId(), given.latest(),
                adminToken), 409);

        assertThat(body.at("/errors/0/code").asText()).isEqualTo("SUPPLY_TRANSFER_DATE_NOT_AFTER_PREVIOUS");
        assertThat(body.at("/errors/0/message").asText()).isNotEmpty();
        assertThat(recordsOf(given.supply())).hasSize(2);
        assertThat(ownerOf(given.supply())).isEqualTo(given.owner().getId());
    }

    @Test
    @DisplayName("SUP-010 a date between the supply's two previous transfers is refused with 409 "
            + "SUPPLY_TRANSFER_DATE_NOT_AFTER_PREVIOUS: the latest one counts, not the first")
    void aDateBeforeTheLatestPreviousTransferIsRefused() throws Exception {
        TransferredSupply given = aSupplyTransferredTwice();

        JsonNode body = refusal(transfer(given.supply(), given.third().getPersonalId(), given.latest().minusDays(1),
                adminToken), 409);

        assertThat(body.at("/errors/0/code").asText()).isEqualTo("SUPPLY_TRANSFER_DATE_NOT_AFTER_PREVIOUS");
        assertThat(recordsOf(given.supply())).hasSize(2);
    }

    @Test
    @DisplayName("SUP-010 a date after the supply's latest transfer is accepted, even when another supply has a "
            + "later transfer")
    void aDateAfterTheLatestTransferIsAccepted_whateverOtherSuppliesHave() throws Exception {
        TransferredSupply given = aSupplyTransferredTwice();
        Supply other = supplyOf(memberOf(community, CommunityRole.COMMUNITY_MEMBER), community);
        transfer(other, given.third().getPersonalId(), TODAY, adminToken).andExpect(status().isOk());

        transfer(given.supply(), given.third().getPersonalId(), given.latest().plusDays(1), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(given.third().getId().toString()));

        assertThat(recordsOf(given.supply())).hasSize(3);
    }

    @Test
    @DisplayName("SUP-008 the supply's owner, who is not an admin, is refused with 403, and nothing is written")
    void theOwnerIsForbidden() throws Exception {
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        transfer(supply, next.getPersonalId(), TODAY, loginUser(owner)).andExpect(status().isForbidden());

        assertNothingWritten(supply, owner);
    }

    @Test
    @DisplayName("SUP-008 a platform admin who is not a member of the supply's community is answered 404")
    void aPlatformAdminWithoutAMembershipIsAnsweredNotFound() throws Exception {
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        transfer(supply, next.getPersonalId(), TODAY, loginAsDefaultPlatformAdmin())
                .andExpect(status().isNotFound());

        assertNothingWritten(supply, owner);
    }

    @Test
    @DisplayName("SUP-008 an admin of another community is answered 404")
    void anAdminOfAnotherCommunityIsAnsweredNotFound() throws Exception {
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Community other = createCommunityRepository.create(CommunityMother.random().build());

        transfer(supply, next.getPersonalId(), TODAY, loginAsCommunityAdmin(other.getId()))
                .andExpect(status().isNotFound());

        assertNothingWritten(supply, owner);
    }

    @Test
    @DisplayName("SUP-008 a plain member of the supply's community who does not own it is answered 404")
    void aPlainMemberIsAnsweredNotFound() throws Exception {
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        transfer(supply, next.getPersonalId(), TODAY, loginUser(next)).andExpect(status().isNotFound());

        assertNothingWritten(supply, owner);
    }

    @Test
    @DisplayName("SUP-008 a request without a token is answered 401")
    void aRequestWithoutATokenIsUnauthorized() throws Exception {
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);

        mockMvc.perform(post(String.format(PATH, supply.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(memberOf(community, CommunityRole.COMMUNITY_MEMBER).getPersonalId(), TODAY)))
                .andExpect(status().isUnauthorized());

        assertNothingWritten(supply, owner);
    }

    @Test
    @DisplayName("SUP-008 an unknown supply is answered 404")
    void anUnknownSupplyIsAnsweredNotFound() throws Exception {
        mockMvc.perform(post(String.format(PATH, UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(memberOf(community, CommunityRole.COMMUNITY_MEMBER).getPersonalId(), TODAY)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SUP-008 the response to a community admin reports canTransferOwnership")
    void theResponseReportsCanTransferOwnershipToTheAdmin() throws Exception {
        Supply supply = supplyOf(memberOf(community, CommunityRole.COMMUNITY_MEMBER), community);
        User next = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        transfer(supply, next.getPersonalId(), TODAY, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canTransferOwnership").value(true));
    }

    @Test
    void aBlankPersonalIdOrAMissingDateIsRefusedWith400() throws Exception {
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(owner, community);

        mockMvc.perform(post(String.format(PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOwnerPersonalId\": \"  \", \"effectiveDate\": \"" + TODAY + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(String.format(PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOwnerPersonalId\": \"" + owner.getPersonalId() + "\"}"))
                .andExpect(status().isBadRequest());

        assertNothingWritten(supply, owner);
    }

    // --- Helpers ---

    private void assertTransferredTo(User next) throws Exception {
        User previous = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(previous, community);

        transfer(supply, next.getPersonalId(), TODAY, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$.code").value(supply.getCode()))
                .andExpect(jsonPath("$.user.id").value(next.getId().toString()))
                .andExpect(jsonPath("$.ownerRecord.fullName").value(next.getFullName()))
                .andExpect(jsonPath("$.ownerRecord.memberNumber").value(next.getNumber()))
                .andExpect(jsonPath("$.ownerRecord.contactEmail").value(next.getEmail()))
                .andExpect(jsonPath("$.community.id").value(community.getId().toString()));

        assertThat(ownerOf(supply)).isEqualTo(next.getId());
        List<Map<String, Object>> records = recordsOf(supply);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("previous_user_id")).isEqualTo(previous.getId());
        assertThat(records.get(0).get("new_user_id")).isEqualTo(next.getId());
    }

    /**
     * A supply transferred from its first owner to a second one, then to its current owner, ten and then five days
     * ago, and a third member of the community to transfer it to next.
     */
    private TransferredSupply aSupplyTransferredTwice() throws Exception {
        User first = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        User second = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        User third = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Supply supply = supplyOf(first, community);
        LocalDate latest = TODAY.minusDays(5);
        transfer(supply, second.getPersonalId(), TODAY.minusDays(10), adminToken).andExpect(status().isOk());
        transfer(supply, owner.getPersonalId(), latest, adminToken).andExpect(status().isOk());
        return new TransferredSupply(supply, owner, third, latest);
    }

    private record TransferredSupply(Supply supply, User owner, User third, LocalDate latest) {
    }

    private User memberOf(Community community, CommunityRole role) {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipRepository.create(community.getId(), user.getId(), role);
        return user;
    }

    private Supply supplyOf(User owner, Community community) {
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }

    private ResultActions transfer(Supply supply, String personalId, LocalDate effectiveDate, String token)
            throws Exception {
        return mockMvc.perform(post(String.format(PATH, supply.getId()))
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(personalId, effectiveDate)));
    }

    private String body(String personalId, LocalDate effectiveDate) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("newOwnerPersonalId", personalId);
        body.put("effectiveDate", effectiveDate.toString());
        return objectMapper.writeValueAsString(body);
    }

    /**
     * The refusal's body without the fields that differ between any two responses.
     */
    private JsonNode refusal(ResultActions result, int status) throws Exception {
        String content = result.andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        ObjectNode body = (ObjectNode) objectMapper.readTree(content);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("message").asText()).isNotEmpty();
        body.remove("timestamp");
        body.remove("traceId");
        return body;
    }

    private void assertNothingWritten(Supply supply, User owner) {
        assertThat(ownerOf(supply)).isEqualTo(owner.getId());
        assertThat(recordsOf(supply)).isEmpty();
    }

    private UUID ownerOf(Supply supply) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT user_id FROM supplies WHERE id = ?", UUID.class, supply.getId());
    }

    /**
     * The test's transaction is the request's, so the records the request wrote are flushed before they are read.
     */
    private List<Map<String, Object>> recordsOf(Supply supply) {
        entityManager.flush();
        return jdbcTemplate.queryForList("SELECT * FROM supply_ownership_transfers WHERE supply_id = ? "
                + "ORDER BY effective_date", supply.getId());
    }
}
