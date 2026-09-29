package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.time.ClockProvider;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC7 (#313). The estimated price both endpoints return is the configured one, not a literal: the
 * configuration is overridden here with a value that is neither the default 0.15 nor representable
 * in cents, so a hard-coded price or a price rounded like the amounts beside it would both fail.
 */
@Transactional
@TestPropertySource(properties = "conluz.supply.tariff.estimated.base-eur-per-kwh=0.1234")
class EstimatedPriceConfigurationTest extends BaseControllerTest {

    private static final String CONFIGURED_PRICE_JSON = "\"estimatedPrice\":{\"eurPerKWh\":0.1234}";

    private static final String ENERGY_METRICS_PATH = "/api/v1/supplies/{supplyId}/energy-metrics";
    private static final String PAYBACK_PATH = "/api/v1/communities/{communityId}/memberships/{userId}/payback";

    /** 2025-01-01T00:00 in Madrid. */
    private static final Instant ACTIVATED_AT = Instant.parse("2024-12-31T23:00:00Z");
    private static final Instant NOW = Instant.parse("2025-04-11T08:00:00Z");

    @MockitoBean
    private ClockProvider clockProvider;

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;

    private final List<String> writtenCups = new ArrayList<>();

    @BeforeEach
    void givenAFixedClock() {
        when(clockProvider.now()).thenReturn(NOW);
    }

    @AfterEach
    void clearWrittenSeries() {
        writtenCups.forEach(influxFixture::clear);
    }

    @Test
    void energyMetricsReturnTheConfiguredEstimatedPrice() throws Exception {
        CommunityEntity community = persistCommunity();
        User owner = persistMember(community.getId());
        SupplyEntity supply = givenSupplyWithSelfConsumption(owner, community, 10f);

        mockMvc.perform(get(ENERGY_METRICS_PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner))
                        .queryParam("startDate", "2025-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2025-02-01T00:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings.tariffSource").value("ESTIMATE"))
                .andExpect(content().string(containsString(CONFIGURED_PRICE_JSON)));
    }

    @Test
    void paybackReturnsTheConfiguredEstimatedPrice() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());
        givenSupplyWithSelfConsumption(member, community, 10f);

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tariffSource").value("ESTIMATE"))
                .andExpect(content().string(containsString(CONFIGURED_PRICE_JSON)));
    }

    // --- fixtures ---

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    /**
     * A community whose coefficient was activated at {@link #ACTIVATED_AT}, which is what gives its
     * members a payback period to price.
     */
    private CommunityEntity persistSharingCommunity() {
        CommunityEntity community = persistCommunity();
        SupplyEntity plantSupply = persistSupply(persistUser(), community);
        PlantEntity plant = plantRepository.save(
                PlantMother.randomPlantEntity().withSupply(plantSupply).build());

        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Estimated price test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.PUBLISHED);
        agreement.setCreatedAt(Instant.now());
        agreement = sharingAgreementRepository.save(agreement);

        saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(plantSupply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(ACTIVATED_AT)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build());

        return community;
    }

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private User persistMember(UUID communityId) {
        User member = persistUser();
        createMembershipService.create(communityId, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    private SupplyEntity persistSupply(User owner, CommunityEntity community) {
        return supplyRepository.save(SupplyEntityMother.random(
                userRepository.getReferenceById(owner.getId()), community));
    }

    private SupplyEntity givenSupplyWithSelfConsumption(User owner, CommunityEntity community,
                                                        float selfConsumptionKWh) {
        SupplyEntity supply = persistSupply(owner, community);
        influxFixture.write(List.of(DatadisConsumptionInfluxFixture.hourlyRecord(
                supply.getCode(), "2025/02/01", "00:00", selfConsumptionKWh, selfConsumptionKWh, 0f)));
        writtenCups.add(supply.getCode());
        return supply;
    }
}
