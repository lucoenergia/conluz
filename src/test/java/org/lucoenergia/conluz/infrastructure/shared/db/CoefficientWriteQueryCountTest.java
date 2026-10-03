package org.lucoenergia.conluz.infrastructure.shared.db;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.sharingagreement.ResolvedCoefficientEntry;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The write endpoints that return {@code PartitionCoefficientResponse} must not pay per row for the
 * response they build.
 *
 * <p>Unlike a listing, a write's own cost legitimately grows with the batch -- a save per row, and a
 * predecessor lookup per activated coefficient -- so comparing one row against five would fail for
 * reasons that have nothing to do with the response. What is pinned instead is the difference: from
 * one row to five, the endpoint may grow by exactly as much as the service calls the controller makes
 * before it builds the response, and not one statement more. Building the response -- the
 * capabilities above all, which load plants and agreements -- therefore costs the same for any
 * batch.</p>
 */
@Transactional
class CoefficientWriteQueryCountTest extends BaseControllerTest {

    private static final LocalDate APPLIED_ON = LocalDate.parse("2024-01-01");
    private static final Instant ACTIVATED = Instant.parse("2023-06-01T00:00:00Z");
    private static final Instant CLOSED = Instant.parse("2023-09-01T00:00:00Z");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private MaterializeSharingAgreementCoefficientsService materializeService;
    @Autowired
    private CoefficientActivationService activationService;
    @Autowired
    private PartitionCoefficientService partitionCoefficientService;

    @Test
    void replacingCoefficientsBuildsItsResponseAtAFixedCost() throws Exception {
        assertResponseCostIsFixed("replace",
                rows -> new Fixture(rows, SharingAgreementStatus.DRAFT, null, null, false),
                fixture -> fixture.request(put(fixture.path("")), replaceBody(fixture)),
                fixture -> materializeService.replaceAllBySupplyId(fixture.plant.getId(), fixture.agreement.getId(),
                        fixture.supplies.stream()
                                .map(supply -> new ResolvedCoefficientEntry(supply.getId(), share(fixture)))
                                .toList()));
    }

    @Test
    void activatingCoefficientsBuildsItsResponseAtAFixedCost() throws Exception {
        assertResponseCostIsFixed("activate",
                rows -> new Fixture(rows, SharingAgreementStatus.PUBLISHED, null, null, true),
                fixture -> fixture.request(post(fixture.path("/activate")),
                        "{\"appliedOn\": \"" + APPLIED_ON + "\", \"coefficientIds\": " + json(fixture.ids) + "}"),
                fixture -> activationService.setValidFrom(fixture.plant.getId(), fixture.agreement.getId(),
                        APPLIED_ON, fixture.ids));
    }

    @Test
    void deactivatingCoefficientsBuildsItsResponseAtAFixedCost() throws Exception {
        assertResponseCostIsFixed("deactivate",
                rows -> new Fixture(rows, SharingAgreementStatus.PUBLISHED, ACTIVATED, null, true),
                fixture -> fixture.request(post(fixture.path("/deactivate")),
                        "{\"coefficientIds\": " + json(fixture.ids) + "}"),
                fixture -> activationService.setValidFrom(fixture.plant.getId(), fixture.agreement.getId(),
                        null, fixture.ids));
    }

    @Test
    void closingCoefficientsBuildsItsResponseAtAFixedCost() throws Exception {
        assertResponseCostIsFixed("close",
                rows -> new Fixture(rows, SharingAgreementStatus.PUBLISHED, ACTIVATED, null, true),
                fixture -> fixture.request(post(fixture.path("/close")),
                        "{\"closedOn\": \"" + APPLIED_ON + "\", \"coefficientIds\": " + json(fixture.ids) + "}"),
                fixture -> activationService.setValidTo(fixture.plant.getId(), fixture.agreement.getId(),
                        APPLIED_ON, fixture.ids));
    }

    @Test
    void reopeningCoefficientsBuildsItsResponseAtAFixedCost() throws Exception {
        assertResponseCostIsFixed("reopen",
                rows -> new Fixture(rows, SharingAgreementStatus.PUBLISHED, ACTIVATED, CLOSED, true),
                fixture -> fixture.request(post(fixture.path("/reopen")),
                        "{\"coefficientIds\": " + json(fixture.ids) + "}"),
                fixture -> activationService.setValidTo(fixture.plant.getId(), fixture.agreement.getId(),
                        null, fixture.ids));
    }

    /**
     * Four fixtures, so each measurement starts from the same state: the endpoint and the service it
     * wraps, each at one row and at five.
     */
    private void assertResponseCostIsFixed(String endpoint, Function<Integer, Fixture> fixtures,
                                           RequestFactory request,
                                           Function<Fixture, List<SupplyPartitionCoefficient>> write)
            throws Exception {
        long endpointGrowth = endpointStatements(request.build(fixtures.apply(5)))
                - endpointStatements(request.build(fixtures.apply(1)));
        long serviceGrowth = serviceStatements(write, fixtures.apply(5))
                - serviceStatements(write, fixtures.apply(1));

        assertEquals(serviceGrowth, endpointGrowth, endpoint + ": from one row to five the service grows by "
                + serviceGrowth + " statements but the endpoint by " + endpointGrowth
                + " -- building the response now issues a query per row");
    }

    private long endpointStatements(MockHttpServletRequestBuilder request) throws Exception {
        startFromAnEmptyPersistenceContext();
        return measured(ThreadStatementCounter.count(() -> {
            mockMvc.perform(request).andExpect(status().isOk());
            entityManager.flush();
        }));
    }

    /**
     * Exactly what the controllers do before building the response: the write, then one detail
     * lookup for what it touched.
     */
    private long serviceStatements(Function<Fixture, List<SupplyPartitionCoefficient>> write, Fixture fixture) {
        startFromAnEmptyPersistenceContext();
        return measured(ThreadStatementCounter.count(() -> {
            partitionCoefficientService.findDetailsInOrderOf(write.apply(fixture));
            entityManager.flush();
        }));
    }

    private void startFromAnEmptyPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Every measured call runs statements, so a zero means the counter is not installed and the
     * comparison would pass vacuously.
     */
    private static long measured(long statements) {
        assertTrue(statements > 0, "No statements were counted: is ThreadStatementCounter installed as the "
                + "Hibernate statement inspector in application-test.properties?");
        return statements;
    }

    private static BigDecimal share(Fixture fixture) {
        return BigDecimal.ONE.divide(BigDecimal.valueOf(fixture.supplies.size()), 6, RoundingMode.DOWN);
    }

    private static String replaceBody(Fixture fixture) {
        return fixture.supplies.stream()
                .map(supply -> "{\"supplyId\": \"" + supply.getId() + "\", \"coefficient\": " + share(fixture) + "}")
                .collect(Collectors.joining(",", "{\"coefficients\": [", "]}"));
    }

    private static String json(List<UUID> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    @FunctionalInterface
    private interface RequestFactory {
        MockHttpServletRequestBuilder build(Fixture fixture) throws Exception;
    }

    /**
     * A plant in a community of its own with one agreement and {@code rows} supplies, each its own
     * timeline, so the rows of a batch never interact.
     */
    private final class Fixture {

        private final Community community = createCommunityRepository.create(CommunityMother.random().build());
        private final User owner = createUserRepository.create(UserMother.randomUser());
        private final Plant plant;
        private final SharingAgreementEntity agreement;
        private final List<Supply> supplies = new ArrayList<>();
        private final List<UUID> ids = new ArrayList<>();

        private Fixture(int rows, SharingAgreementStatus status, Instant validFrom, Instant validTo,
                        boolean withCoefficients) {
            Supply plantSupply = supply();
            plant = createPlantRepository.create(PlantMother.random(plantSupply).build(),
                    SupplyId.of(plantSupply.getId()));
            agreement = new SharingAgreementEntity();
            agreement.setId(UUID.randomUUID());
            agreement.setPlant(plantRepository.getReferenceById(plant.getId()));
            agreement.setName("Agreement " + UUID.randomUUID());
            agreement.setStatus(status);
            agreement.setCreatedAt(Instant.now());
            sharingAgreementRepository.save(agreement);
            for (int i = 0; i < rows; i++) {
                Supply supply = supply();
                supplies.add(supply);
                if (withCoefficients) {
                    ids.add(saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                            .withId(UUID.randomUUID())
                            .withSupplyId(supply.getId())
                            .withPlantId(plant.getId())
                            .withSharingAgreementId(agreement.getId())
                            .withCoefficient(BigDecimal.ONE)
                            .withValidFrom(validFrom)
                            .withValidTo(validTo)
                            .withCreatedAt(Instant.now())
                            .build()).getId());
                }
            }
        }

        private Supply supply() {
            return createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()),
                    community.getId());
        }

        private String path(String action) {
            return "/api/v1/plants/" + plant.getId() + "/sharing-agreements/" + agreement.getId()
                    + "/partition-coefficients" + action;
        }

        private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder, String body)
                throws Exception {
            return builder.header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body);
        }
    }
}
