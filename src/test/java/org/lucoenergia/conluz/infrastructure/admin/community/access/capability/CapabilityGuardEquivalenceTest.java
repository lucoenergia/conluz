package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CommunityReference;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SharingAgreementReference;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyReference;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;

/**
 * Every capability must equal the outcome of the endpoint it describes. Two evaluations of one rule
 * that disagree is the failure mode this whole design exists to prevent — a client that hides a
 * control the server would have allowed is merely annoying, but one that offers a control the server
 * refuses is a bug the user sees.
 *
 * <p>So each capability is computed twice, both ways, for every caller in the matrix: once by the
 * assembler over already-loaded entities, and once by the guard as it runs during
 * {@code @PreAuthorize}. A guard answers three ways — {@code true}, {@code false}, or a
 * {@code *NotFoundException} standing for a 404 — and the last two are both "not offered", so a
 * thrown not-found counts as {@code false}.</p>
 *
 * <p>The callers are the ones where the two could plausibly diverge: the platform admin appears
 * twice, once inside the community and once outside it, because almost every asymmetry in this
 * codebase turns on that distinction.</p>
 */
@Transactional
class CapabilityGuardEquivalenceTest extends BaseIntegrationTest {

    @Autowired
    private CommunityAccessGuard guard;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private GetSharingAgreementRepository getSharingAgreementRepository;
    @Autowired
    private GetMembershipsRepository getMembershipsRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;

    @Autowired
    private CommunityCapabilitiesAssembler communityAssembler;
    @Autowired
    private SupplyCapabilitiesAssembler supplyAssembler;
    @Autowired
    private PlantCapabilitiesAssembler plantAssembler;
    @Autowired
    private SharingAgreementCapabilitiesAssembler agreementAssembler;
    @Autowired
    private UserCapabilitiesAssembler userAssembler;
    @Autowired
    private MembershipCapabilitiesAssembler membershipAssembler;
    @Autowired
    private PlatformCapabilitiesAssembler platformAssembler;
    @Autowired
    private PartitionCoefficientCapabilitiesAssembler partitionCoefficientAssembler;

    private Community communityA;
    private Community communityB;
    private User owner;
    private Supply supply;
    private Plant plant;
    private SharingAgreement agreement;
    private Map<String, User> callers;

    @BeforeEach
    void setUp() {
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());

        owner = persistUser();
        createMembershipService.create(communityA.getId(), owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()),
                communityA.getId());
        plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        agreement = persistAgreement(plant);

        callers = new LinkedHashMap<>();
        callers.put("platform admin, member of A", platformAdminMemberOf(communityA));
        callers.put("platform admin, not a member", platformAdmin());
        callers.put("community admin of A", memberOf(communityA, CommunityRole.COMMUNITY_ADMIN));
        callers.put("plain member of A", memberOf(communityA, CommunityRole.COMMUNITY_MEMBER));
        callers.put("owner of the supply", owner);
        callers.put("member of B", memberOf(communityB, CommunityRole.COMMUNITY_MEMBER));
        callers.put("disabled member of A", disabledMemberOf(communityA));
    }

    @Test
    void theCommunityCapabilitiesEqualTheirGuards() {
        UUID id = communityA.getId();
        forEachCaller(caller -> {
            CommunityCapabilitiesResponse capabilities = communityAssembler.assemble(caller, id);
            assertSame("canRead", capabilities.isCanRead(), () -> guard.canReadCommunity(id));
            assertSame("canUpdate", capabilities.isCanUpdate(), () -> guard.canUpdateCommunity(id));
            assertSame("canEnable", capabilities.isCanEnable(), () -> guard.canEnableCommunity(id));
            assertSame("canDisable", capabilities.isCanDisable(), () -> guard.canDisableCommunity(id));
            assertSame("canManage", capabilities.isCanManage(), () -> guard.canManageCommunity(id));
            assertSame("canManageMemberships", capabilities.isCanManageMemberships(),
                    () -> guard.canManageMemberships(id));
            assertSame("canManageMembershipInvestment", capabilities.isCanManageMembershipInvestment(),
                    () -> guard.canManageMembershipInvestment(id));
            assertSame("canListPlants", capabilities.isCanListPlants(), () -> guard.canListPlants(id));
            assertSame("canCreateUsers", capabilities.isCanCreateUsers(), () -> guard.canCreateUserIn(id));
            assertSame("canReadProduction", capabilities.isCanReadProduction(),
                    () -> guard.canReadCommunityProduction(id));
            assertSame("canListSupplies", capabilities.isCanListSupplies(), () -> guard.canListSupplies(id));
        });
    }

    @Test
    void theSupplyCapabilitiesEqualTheirGuards() {
        UUID id = supply.getId();
        forEachCaller(caller -> {
            SupplyCapabilitiesResponse capabilities = supplyAssembler.assemble(caller, supply);
            assertSame("canRead", capabilities.isCanRead(), () -> guard.canReadSupply(id));
            assertSame("canEdit", capabilities.isCanEdit(), () -> guard.canEditSupply(id));
            assertSame("canReadPartitionCoefficients", capabilities.isCanReadPartitionCoefficients(),
                    () -> guard.canReadSupplyPartitionCoefficients(id));
            assertSame("canCreatePlant", capabilities.isCanCreatePlant(),
                    () -> guard.canCreatePlant(supply.getCode()));
        });
    }

    @Test
    void thePlantCapabilitiesEqualTheirGuards() {
        UUID id = plant.getId();
        forEachCaller(caller -> {
            PlantCapabilitiesResponse capabilities = plantAssembler.assemble(caller, plant);
            assertSame("canRead", capabilities.isCanRead(), () -> guard.canReadPlant(id));
            assertSame("canManage", capabilities.isCanManage(), () -> guard.canManagePlant(id));
            assertSame("canListSharingAgreements", capabilities.isCanListSharingAgreements(),
                    () -> guard.canListSharingAgreements(id));
            assertSame("canManageSharingAgreements", capabilities.isCanManageSharingAgreements(),
                    () -> guard.canManageSharingAgreement(id));
            // The reason this one exists: the plant's supply reference carries no owner.
            assertSame("canReadSupply", capabilities.isCanReadSupply(),
                    () -> guard.canReadSupply(supply.getId()));
        });
    }

    @Test
    void theSharingAgreementCapabilitiesEqualTheirGuards() {
        UUID plantId = plant.getId();
        UUID agreementId = agreement.getId();
        forEachCaller(caller -> {
            SharingAgreementCapabilitiesResponse capabilities =
                    agreementAssembler.assemble(caller, plant, agreement);
            assertSame("canRead", capabilities.isCanRead(),
                    () -> guard.canReadSharingAgreement(plantId, agreementId));
            assertSame("canManage", capabilities.isCanManage(),
                    () -> guard.canManageSharingAgreement(plantId, agreementId));
        });
    }

    /**
     * The period is built in memory because the capability is decided from its plant and agreement
     * ids alone; the assembler loads both from the database exactly as a request does.
     */
    @Test
    void thePartitionCoefficientCapabilitiesEqualTheirGuards() {
        UUID plantId = plant.getId();
        UUID agreementId = agreement.getId();
        SupplyPartitionCoefficientDetail period = periodOf(plant, agreement);
        forEachCaller(caller -> {
            PartitionCoefficientCapabilitiesResponse capabilities =
                    partitionCoefficientAssembler.assembleAll(caller, List.of(period)).get(period.getId());
            // The reason this one exists: the period's sharingAgreement reference carries no
            // capabilities, and this is the guard of the endpoint that following it calls.
            assertSame("canReadSharingAgreement", capabilities.isCanReadSharingAgreement(),
                    () -> guard.canReadSharingAgreement(plantId, agreementId));
        });
    }

    /**
     * conluz-web links a period to its agreement's page, and that route is guarded client-side on the
     * plant's {@code canListSharingAgreements}. The capability predicts the API endpoint, not the
     * route, so it is only a safe basis for showing the link while the one implies the other. Today
     * they are the same rule; this fails the moment somebody splits them in a way that breaks it.
     */
    @Test
    void readingACoefficientsSharingAgreementImpliesListingThePlantsAgreements() {
        UUID plantId = plant.getId();
        SupplyPartitionCoefficientDetail period = periodOf(plant, agreement);
        List<String> exercised = new ArrayList<>();
        forEachCaller(caller -> {
            boolean canReadSharingAgreement = partitionCoefficientAssembler
                    .assembleAll(caller, List.of(period)).get(period.getId()).isCanReadSharingAgreement();
            if (!canReadSharingAgreement) {
                return;
            }
            exercised.add(currentCaller);
            boolean canListSharingAgreements = guardOutcome(() -> guard.canListSharingAgreements(plantId));
            assertTrue(canListSharingAgreements, () -> String.format(
                    "partitionCoefficient.canReadSharingAgreement is true for '%s' but the "
                            + "canListSharingAgreements guard refuses them: the capability predicts a web "
                            + "route (CapabilityRoute on plant.canListSharingAgreements) that would redirect "
                            + "home. Keep canReadSharingAgreement => canListSharingAgreements, or have the "
                            + "web gate the link on both.", currentCaller));
        });
        assertTrue(!exercised.isEmpty(), "no caller could read the agreement, so the implication was never tested");
    }

    @Test
    void theUserCapabilitiesEqualTheirGuards() {
        UUID targetId = owner.getId();
        forEachCaller(caller -> {
            UserCapabilitiesResponse capabilities =
                    userAssembler.assembleFetchingMemberships(caller, targetId);
            assertSame("canRead", capabilities.isCanRead(), () -> guard.canReadUser(targetId));
            assertSame("canEdit", capabilities.isCanEdit(), () -> guard.canEditUser(targetId));
            assertSame("canDelete", capabilities.isCanDelete(), () -> guard.canDeleteUser(targetId));
            assertSame("canEnable", capabilities.isCanEnable(), () -> guard.canEnableUser(targetId));
            assertSame("canDisable", capabilities.isCanDisable(), () -> guard.canDisableUser(targetId));
            assertSame("canGrantPlatformAdmin", capabilities.isCanGrantPlatformAdmin(),
                    () -> guard.canGrantPlatformAdmin(targetId));
            assertSame("canRevokePlatformAdmin", capabilities.isCanRevokePlatformAdmin(),
                    () -> guard.canRevokePlatformAdmin(targetId));
            assertSame("canListSupplies", capabilities.isCanListSupplies(),
                    () -> guard.canListSuppliesOfUser(targetId));
        });
    }

    /**
     * Also covers the self case, which the matrix above cannot reach: every caller acting on
     * themselves, where canDelete and canRevokePlatformAdmin must both come out false however
     * privileged they are.
     */
    @Test
    void theUserCapabilitiesEqualTheirGuardsWhenTheTargetIsTheCallerThemselves() {
        forEachCaller(caller -> {
            UUID selfId = caller.getId();
            UserCapabilitiesResponse capabilities =
                    userAssembler.assembleFetchingMemberships(caller, selfId);
            assertSame("canRead", capabilities.isCanRead(), () -> guard.canReadUser(selfId));
            assertSame("canEdit", capabilities.isCanEdit(), () -> guard.canEditUser(selfId));
            assertSame("canDelete", capabilities.isCanDelete(), () -> guard.canDeleteUser(selfId));
            assertSame("canRevokePlatformAdmin", capabilities.isCanRevokePlatformAdmin(),
                    () -> guard.canRevokePlatformAdmin(selfId));
        });
    }

    @Test
    void theMembershipCapabilitiesEqualTheirGuards() {
        CommunityMembership membership = getMembershipsRepository
                .findByUserIdAndCommunityId(owner.getId(), communityA.getId()).orElseThrow();
        UUID communityId = communityA.getId();
        UUID userId = owner.getId();

        forEachCaller(caller -> {
            MembershipCapabilitiesResponse capabilities = membershipAssembler.assemble(caller, membership);
            assertSame("canUpdateRole", capabilities.isCanUpdateRole(),
                    () -> guard.canManageMemberships(communityId));
            assertSame("canDelete", capabilities.isCanDelete(),
                    () -> guard.canManageMemberships(communityId));
            assertSame("canManageInvestment", capabilities.isCanManageInvestment(),
                    () -> guard.canManageMembershipInvestment(communityId));
            assertSame("canReadPayback", capabilities.isCanReadPayback(),
                    () -> guard.canReadMembershipPayback(communityId, userId));
        });
    }

    @Test
    void thePlatformCapabilitiesEqualTheirGuards() {
        forEachCaller(caller -> {
            PlatformCapabilitiesResponse capabilities = platformAssembler.assemble(caller);
            assertSame("canCreateCommunity", capabilities.isCanCreateCommunity(),
                    () -> guard.canCreateCommunity());
            assertSame("canListUsers", capabilities.isCanListUsers(), () -> guard.canListUsers());
            // The null is the decision itself: POST /api/v1/users carries an optional communityId,
            // so this is the guard call the endpoint makes when none is given.
            assertSame("canCreateUsers", capabilities.isCanCreateUsers(),
                    () -> guard.canCreateUserIn(null));
            // canAdministerPlatform is deliberately absent: it has no guard to be equal to -- it
            // gates a surface rather than an endpoint -- exactly as community.canCreatePlants is
            // absent from theCommunityCapabilitiesEqualTheirGuards. Its anchor is
            // MembershipAndPlatformCapabilitiesAssemblerTest instead.
        });
    }

    // --- the matrix ---

    private String currentCaller;

    /**
     * Both sides are handed the <em>same</em> principal, loaded the way a request loads it. That is
     * not a convenience: a fixture {@code User} straight from the builder carries no memberships,
     * so passing it to the assembler while the guard reads the real principal would compare two
     * different callers and report a disagreement that does not exist.
     */
    private void forEachCaller(java.util.function.Consumer<User> assertions) {
        List<String> checked = new ArrayList<>();
        for (Map.Entry<String, User> entry : callers.entrySet()) {
            currentCaller = entry.getKey();
            User principal = authenticate(entry.getValue());
            try {
                assertions.accept(principal);
            } finally {
                SecurityContextHolder.clearContext();
            }
            checked.add(entry.getKey());
        }
        assertEquals(callers.size(), checked.size());
        assertTrue(checked.size() >= 7, "the matrix lost callers: " + checked);
    }

    /**
     * Compares the assembler's answer with the guard's. A guard that throws a {@code *NotFoundException}
     * is saying the caller cannot see the object, which for a capability is the same as no.
     */
    private void assertSame(String capability, boolean assembled, BooleanSupplier guardCall) {
        boolean expected = guardOutcome(guardCall);
        assertEquals(expected, assembled,
                () -> String.format("%s disagrees with its guard for '%s': assembler said %s, guard said %s",
                        capability, currentCaller, assembled, expected));
    }

    private boolean guardOutcome(BooleanSupplier guardCall) {
        try {
            return guardCall.getAsBoolean();
        } catch (RuntimeException e) {
            assertTrue(e.getClass().getSimpleName().endsWith("NotFoundException"),
                    () -> "the guard threw something other than a not-found: " + e);
            return false;
        }
    }

    /**
     * The principal is loaded exactly as a request loads it, so both sides see the same memberships.
     */
    private User authenticate(User user) {
        User principal = (User) userDetailsService.loadUserByUsername(user.getPersonalId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return principal;
    }

    // --- fixtures ---

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private User platformAdmin() {
        return grantPlatformAdmin(persistUser());
    }

    /**
     * Persisted, unlike {@code CapabilityFixtures.platformAdminMemberOf} which builds the same
     * caller in memory for the unit assembler tests. The two are not interchangeable: authenticate()
     * below loads the principal through UserDetailsService, so the row has to exist, and the flag is
     * granted the way production grants it. Adjust one and check the other.
     */
    private User platformAdminMemberOf(Community community) {
        return grantPlatformAdmin(memberOf(community, CommunityRole.COMMUNITY_MEMBER));
    }

    /**
     * Through the repository the grant endpoint uses, so the flag is set the way production sets it
     * rather than by re-creating the row.
     */
    private User grantPlatformAdmin(User user) {
        managePlatformAdminRepository.grant(UserId.of(user.getId()));
        user.setPlatformAdmin(true);
        return user;
    }

    private User memberOf(Community community, CommunityRole role) {
        User user = persistUser();
        createMembershipService.create(community.getId(), user.getId(), role);
        return user;
    }

    private User disabledMemberOf(Community community) {
        User user = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        CommunityMembershipEntity membership = membershipJpaRepository
                .findByUserIdAndCommunityId(user.getId(), community.getId()).orElseThrow();
        membership.setEnabled(false);
        membershipJpaRepository.save(membership);
        return user;
    }

    private SupplyPartitionCoefficientDetail periodOf(Plant plant, SharingAgreement agreement) {
        return new SupplyPartitionCoefficientDetail(
                new SupplyPartitionCoefficient.Builder()
                        .withId(UUID.randomUUID())
                        .withSupplyId(supply.getId())
                        .withPlantId(plant.getId())
                        .withSharingAgreementId(agreement.getId())
                        .withCoefficient(BigDecimal.ONE)
                        .withValidFrom(Instant.parse("2025-01-01T00:00:00Z"))
                        .withCreatedAt(Instant.now())
                        .build(),
                new SupplyReference(supply.getId(), supply.getCode(), supply.getName()),
                new CommunityReference(communityA.getId(), communityA.getName()),
                new PlantReference(plant.getId(), plant.getName()),
                new SharingAgreementReference(agreement.getId(), agreement.getName(), agreement.getStatus()));
    }

    private SharingAgreement persistAgreement(Plant plant) {
        PlantEntity plantEntity = plantRepository.getReferenceById(plant.getId());
        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(UUID.randomUUID());
        entity.setPlant(plantEntity);
        entity.setName("Agreement " + UUID.randomUUID());
        entity.setStatus(SharingAgreementStatus.DRAFT);
        entity.setCreatedAt(Instant.now());
        sharingAgreementRepository.save(entity);
        return getSharingAgreementRepository.findById(entity.getId()).orElseThrow();
    }
}
