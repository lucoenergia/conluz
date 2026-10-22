package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * A pending or disabled membership grants nothing: for every guard, its holder gets the answer a
 * person with no membership at all gets. Each action is first checked against a caller holding the
 * same role through an active membership, which must be allowed; otherwise "refused like a
 * non-member" would hold for an action nobody can perform, and prove nothing.
 */
@Transactional
class InactiveMembershipAccessIntegrationTest extends BaseControllerTest {

    private enum Outcome { ALLOWED, REFUSED, NOT_FOUND }

    @Autowired
    private CommunityAccessGuard guard;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;

    private Community community;
    private User owner;
    private Supply supply;
    private Plant plant;
    private SharingAgreement agreement;
    private User nonMember;

    @BeforeEach
    void setUp() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()),
                community.getId());
        plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build());
        nonMember = persistUser();
    }

    @Test
    @DisplayName("MEM-004 a pending admin is refused every community-admin action, as a non-member is")
    void aPendingAdminIsRefusedAsANonMember() {
        User pendingAdmin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        MembershipStateTestSupport.markPending(membershipJpaRepository, pendingAdmin.getId(), community.getId());

        assertTreatedAsANonMember(adminActions(), memberOf(community, CommunityRole.COMMUNITY_ADMIN), pendingAdmin);
    }

    @Test
    @DisplayName("MEM-004 a disabled admin is refused every community-admin action, as a non-member is")
    void aDisabledAdminIsRefusedAsANonMember() {
        User disabledAdmin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        MembershipStateTestSupport.markDisabled(membershipJpaRepository, disabledAdmin.getId(), community.getId());

        assertTreatedAsANonMember(adminActions(), memberOf(community, CommunityRole.COMMUNITY_ADMIN), disabledAdmin);
    }

    @Test
    @DisplayName("MEM-004 a pending member is refused every member read, as a non-member is")
    void aPendingMemberIsRefusedAsANonMember() {
        User pendingMember = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(membershipJpaRepository, pendingMember.getId(), community.getId());

        assertTreatedAsANonMember(memberReads(), memberOf(community, CommunityRole.COMMUNITY_MEMBER), pendingMember);
    }

    @Test
    @DisplayName("MEM-004 a disabled member is refused every member read, as a non-member is")
    void aDisabledMemberIsRefusedAsANonMember() {
        User disabledMember = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markDisabled(membershipJpaRepository, disabledMember.getId(), community.getId());

        assertTreatedAsANonMember(memberReads(), memberOf(community, CommunityRole.COMMUNITY_MEMBER), disabledMember);
    }

    @Test
    @DisplayName("MEM-004 reading the community as a pending member answers what a non-member gets")
    void readingTheCommunityAsAPendingMemberAnswersAsANonMember() throws Exception {
        User pendingMember = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(membershipJpaRepository, pendingMember.getId(), community.getId());
        String url = "/api/v1/communities/" + community.getId();

        int asNonMember = mockMvc.perform(get(url).header("Authorization", loginUser(nonMember)))
                .andDo(print()).andReturn().getResponse().getStatus();
        int asActiveMember = mockMvc.perform(get(url)
                        .header("Authorization", loginUser(memberOf(community, CommunityRole.COMMUNITY_MEMBER))))
                .andDo(print()).andReturn().getResponse().getStatus();
        int asPendingMember = mockMvc.perform(get(url).header("Authorization", loginUser(pendingMember)))
                .andDo(print()).andReturn().getResponse().getStatus();

        assertEquals(200, asActiveMember);
        assertEquals(404, asNonMember);
        assertEquals(asNonMember, asPendingMember);
    }

    // --- actions ---

    /**
     * One community-admin action per policy area.
     */
    private Map<String, BooleanSupplier> adminActions() {
        Map<String, BooleanSupplier> actions = new LinkedHashMap<>();
        actions.put("memberships: manage the community's memberships", () -> guard.canManageMemberships(community.getId()));
        actions.put("memberships: enable or disable a member's membership",
                () -> guard.canChangeMemberState(community.getId(), owner.getId()));
        actions.put("users: list users", () -> guard.canListUsers());
        actions.put("plants: create a plant", () -> guard.canCreatePlant(supply.getCode()));
        actions.put("plants: manage a plant", () -> guard.canManagePlant(plant.getId()));
        actions.put("sharing agreements: manage an agreement",
                () -> guard.canManageSharingAgreement(plant.getId(), agreement.getId()));
        actions.put("supplies: edit a supply", () -> guard.canEditSupply(supply.getId()));
        actions.put("community management: manage the community", () -> guard.canManageCommunity(community.getId()));
        return actions;
    }

    /**
     * What any member of the community may read.
     */
    private Map<String, BooleanSupplier> memberReads() {
        Map<String, BooleanSupplier> reads = new LinkedHashMap<>();
        reads.put("read the community", () -> guard.canReadCommunity(community.getId()));
        reads.put("read the community's production", () -> guard.canReadCommunityProduction(community.getId()));
        reads.put("list the community's supplies", () -> guard.canListSupplies(community.getId()));
        reads.put("list the community's plants", () -> guard.canListPlants(community.getId()));
        reads.put("read a plant", () -> guard.canReadPlant(plant.getId()));
        reads.put("be a member of the community", () -> guard.isMemberOfCommunity(community.getId()));
        reads.put("see the community among the visible ones",
                () -> guard.visibleCommunityIds().contains(community.getId()));
        return reads;
    }

    // --- assertions ---

    private void assertTreatedAsANonMember(Map<String, BooleanSupplier> actions, User activeCaller, User inactiveCaller) {
        actions.forEach((action, call) -> {
            Outcome asActive = outcome(activeCaller, call);
            Outcome asNonMember = outcome(nonMember, call);
            Outcome asInactive = outcome(inactiveCaller, call);

            assertEquals(Outcome.ALLOWED, asActive, () -> action + ": the active caller must be allowed");
            assertEquals(asNonMember, asInactive, () -> action + ": the inactive caller must be answered as a non-member");
        });
    }

    /**
     * The principal is loaded the way a request loads it, so the guard reads the memberships as stored.
     */
    private Outcome outcome(User caller, BooleanSupplier call) {
        User principal = (User) userDetailsService.loadUserByUsername(caller.getPersonalId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            return call.getAsBoolean() ? Outcome.ALLOWED : Outcome.REFUSED;
        } catch (RuntimeException e) {
            if (e.getClass().getSimpleName().endsWith("NotFoundException")) {
                return Outcome.NOT_FOUND;
            }
            throw e;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // --- fixtures ---

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private User memberOf(Community target, CommunityRole role) {
        User user = persistUser();
        createMembershipRepository.create(target.getId(), user.getId(), role);
        return user;
    }
}
