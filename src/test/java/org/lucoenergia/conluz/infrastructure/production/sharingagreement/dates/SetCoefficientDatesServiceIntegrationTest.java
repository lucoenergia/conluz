package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationError;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationException;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationService;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDate;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.AgreementStatusChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.CoefficientPeriodChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.CommunityStartChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.LeavingClosureChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.NeighbourChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesRequest;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.LeavingClosureDate;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.LeavingClosureSource;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.PreviewCoefficientDatesService;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.SetCoefficientDatesService;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Setting an agreement's coefficient dates and leaving closures (#583), against PostgreSQL. Timelines are built the
 * way production builds them: pending rows as materialisation writes them, then the activation, close and reopen
 * services. Every write scenario first runs the preview on the same data and checks it changed nothing and announced
 * exactly what the write then did (SHA-014). Test "today" is 2026-01-01 ({@code MutableClockConfiguration}).
 */
@Transactional
class SetCoefficientDatesServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SetCoefficientDatesService setService;
    @Autowired
    private PreviewCoefficientDatesService previewService;
    @Autowired
    private CoefficientActivationService activationService;
    @Autowired
    private GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private PublishSharingAgreementRepository publishSharingAgreementRepository;
    @Autowired
    private GetSharingAgreementRepository getSharingAgreementRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private ZoneResolver zoneResolver;

    private Community community;
    private Plant plant;
    private Supply x;
    private Supply y;
    private Supply z;
    private Supply w;

    @BeforeEach
    void setUp() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        plant = createPlant();
        x = createSupply();
        y = createSupply();
        z = createSupply();
        w = createSupply();
    }

    // ---- SHA-012 / AC1 ----

    @Test
    @DisplayName("SHA-012 example (a): staggered dates are each placed, a listed leaving supply is closed at its date")
    void staggeredDates() {
        Agreement c = activatedAgreement("2025-01-01", w);
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        // W holds a pending coefficient of B outside the request, so it is not leaving B: C's row stays open.
        Agreement b = pendingAgreement(x, y, w);

        CoefficientDatesEffects effects = previewThenWrite(b,
                List.of(date(b, x, "2025-03-01"), date(b, y, "2025-03-05")),
                List.of(closure(z, "2025-03-03")));

        assertThat(timeline(x)).containsExactly(period(a.of(x), "2025-01-01", "2025-03-01"),
                period(b.of(x), "2025-03-01", null));
        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", "2025-03-05"),
                period(b.of(y), "2025-03-05", null));
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-03-03"));
        assertThat(timeline(w)).containsExactly(period(c.of(w), "2025-01-01", null));
        assertThat(statusOf(a)).isEqualTo(SharingAgreementStatus.SUPERSEDED);
        assertThat(statusOf(b)).isEqualTo(SharingAgreementStatus.PUBLISHED);
        assertThat(statusOf(c)).isEqualTo(SharingAgreementStatus.PUBLISHED);

        assertThat(effects.coefficients()).containsExactly(
                new CoefficientPeriodChange(b.of(x), x.getId(), null, null, at("2025-03-01"), null, false),
                new CoefficientPeriodChange(b.of(y), y.getId(), null, null, at("2025-03-05"), null, false));
        assertThat(effects.neighbours()).containsExactly(
                new NeighbourChange(a.of(x), x.getId(), a.id(), null, at("2025-03-01")),
                new NeighbourChange(a.of(y), y.getId(), a.id(), null, at("2025-03-05")));
        assertThat(effects.leavingClosures()).containsExactly(new LeavingClosureChange(z.getId(), a.of(z), a.id(),
                null, at("2025-03-03"), LeavingClosureSource.REQUEST));
        assertThat(effects.agreements()).containsExactlyInAnyOrder(
                new AgreementStatusChange(a.id(), SharingAgreementStatus.PUBLISHED, SharingAgreementStatus.SUPERSEDED),
                new AgreementStatusChange(b.id(), SharingAgreementStatus.PUBLISHED, SharingAgreementStatus.PUBLISHED));
        assertThat(effects.communityStart()).isEqualTo(new CommunityStartChange(at("2025-01-01"), at("2025-01-01")));
    }

    @Test
    @DisplayName("SHA-012 an active coefficient is corrected and a pending one activated in the same request")
    void correctsAndActivatesInOneRequest() {
        Agreement a = activatedAgreement("2025-01-01", x, y);
        Agreement b = pendingAgreement(x, y);
        activate(b, "2025-03-01", x);

        CoefficientDatesEffects effects = previewThenWrite(b,
                List.of(date(b, x, "2025-02-01"), date(b, y, "2025-04-01")), List.of());

        assertThat(timeline(x)).containsExactly(period(a.of(x), "2025-01-01", "2025-02-01"),
                period(b.of(x), "2025-02-01", null));
        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", "2025-04-01"),
                period(b.of(y), "2025-04-01", null));
        assertThat(effects.coefficients()).containsExactly(
                new CoefficientPeriodChange(b.of(x), x.getId(), at("2025-03-01"), null, at("2025-02-01"), null, false),
                new CoefficientPeriodChange(b.of(y), y.getId(), null, null, at("2025-04-01"), null, false));
    }

    // ---- SHA-013 / AC2, AC3 ----

    @Test
    @DisplayName("SHA-013 example (b): on a first activation, an omitted leaving supply is closed at the earliest date, marked DEFAULT")
    void defaultClosure() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x, y);

        CoefficientDatesEffects effects = previewThenWrite(b,
                List.of(date(b, x, "2025-03-01"), date(b, y, "2025-03-05")), List.of());

        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-03-01"));
        assertThat(effects.leavingClosures()).containsExactly(new LeavingClosureChange(z.getId(), a.of(z), a.id(),
                null, at("2025-03-01"), LeavingClosureSource.DEFAULT));
    }

    @Test
    @DisplayName("SHA-013 on a later request, an omitted leaving supply is untouched")
    void laterRequestLeavesOmittedSuppliesUntouched() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x, y);
        activate(b, "2025-03-01", x);
        reopen(a, z);

        CoefficientDatesEffects effects = previewThenWrite(b, List.of(date(b, y, "2025-03-05")), List.of());

        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", null));
        assertThat(effects.leavingClosures()).isEmpty();
    }

    @Test
    @DisplayName("SHA-013 example (c): a leaving closure made by SHA-009 is shortened to the listed date, with no reopen")
    void shortensASha009Closure() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x, y);
        activate(b, "2025-06-01", x, y);
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-06-01"));

        CoefficientDatesEffects effects = previewThenWrite(b, List.of(), List.of(closure(z, "2025-05-01")));

        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-05-01"));
        assertThat(effects.leavingClosures()).containsExactly(new LeavingClosureChange(z.getId(), a.of(z), a.id(),
                at("2025-06-01"), at("2025-05-01"), LeavingClosureSource.REQUEST));
        assertThat(effects.agreements()).containsExactly(
                new AgreementStatusChange(a.id(), SharingAgreementStatus.SUPERSEDED, SharingAgreementStatus.SUPERSEDED));
    }

    @Test
    @DisplayName("SHA-013 example (c): a leaving closure is extended to the listed date when the supply has no later row")
    void extendsASha009Closure() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x, y);
        activate(b, "2025-06-01", x, y);

        CoefficientDatesEffects effects = previewThenWrite(b, List.of(), List.of(closure(z, "2025-07-01")));

        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-07-01"));
        assertThat(effects.leavingClosures()).extracting(LeavingClosureChange::validToAfter)
                .containsExactly(at("2025-07-01"));
    }

    @Test
    @DisplayName("SHA-013 example (c): extending a leaving closure past the supply's next start is refused as an overlap")
    void refusesAnExtensionPastTheNextStart() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x, y);
        activate(b, "2025-06-01", x, y);
        // C holds every supply, so activating only Z closes nothing (SHA-009): Z's next start is 2025-06-15.
        Agreement c = pendingAgreement(x, y, z);
        activate(c, "2025-06-15", z);
        State before = state();

        CoefficientDatesRequest request = request(List.of(), List.of(closure(z, "2025-07-01")));
        List<CoefficientActivationError> previewErrors = refusedByPreview(b, request);
        List<CoefficientActivationError> writeErrors = refusedByWrite(b, request);

        assertThat(writeErrors).isEqualTo(previewErrors);
        assertThat(writeErrors).extracting(CoefficientActivationError::getCode)
                .containsExactly(CoefficientActivationErrorCode.PERIOD_OVERLAP);
        assertThat(writeErrors.get(0).getParams()).containsEntry("coefficientId", a.of(z).toString())
                .containsEntry("conflictingCoefficientId", c.of(z).toString());
        assertThat(state()).isEqualTo(before);
    }

    @Test
    @DisplayName("SHA-013 extending a leaving closure up to the supply's next start is accepted")
    void extendsUpToTheNextStart() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x, y);
        activate(b, "2025-06-01", x, y);
        Agreement c = pendingAgreement(x, y, z);
        activate(c, "2025-06-15", z);

        previewThenWrite(b, List.of(), List.of(closure(z, "2025-06-15")));

        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-06-15"),
                period(c.of(z), "2025-06-15", null));
    }

    // ---- SHA-014 / AC4, AC5 ----

    @Test
    @DisplayName("SHA-014 the community start moves when the request moves the community's earliest validFrom")
    void reportsAMovedCommunityStart() {
        activatedAgreement("2025-01-01", x, y);
        Agreement older = pendingAgreement(x);

        CoefficientDatesEffects effects = previewThenWrite(older, List.of(date(older, x, "2024-06-01")), List.of());

        assertThat(effects.communityStart()).isEqualTo(new CommunityStartChange(at("2025-01-01"), at("2024-06-01")));
        assertThat(getCoefficientRepository.findEarliestValidFromByCommunityId(community.getId()))
                .contains(at("2024-06-01"));
    }

    @Test
    @DisplayName("SHA-014 the community start is reported unchanged when another plant of the community starts earlier")
    void reportsAnUnchangedCommunityStart() {
        Plant otherPlant = createPlant();
        Supply v = createSupply();
        Agreement elsewhere = pendingAgreement(otherPlant, v);
        activationService.setValidFrom(otherPlant.getId(), elsewhere.id(), LocalDate.parse("2024-01-01"),
                List.of(elsewhere.of(v)));
        activatedAgreement("2025-01-01", x, y);
        Agreement older = pendingAgreement(x);

        CoefficientDatesEffects effects = previewThenWrite(older, List.of(date(older, x, "2024-06-01")), List.of());

        assertThat(effects.communityStart()).isEqualTo(new CommunityStartChange(at("2024-01-01"), at("2024-01-01")));
    }

    @Test
    @DisplayName("SHA-014 a request that changes nothing reports no effect and writes nothing")
    void aNoOpRequest() {
        Agreement b = activatedAgreement("2025-01-01", x, y);

        CoefficientDatesEffects effects = previewThenWrite(b, List.of(date(b, x, "2025-01-01")), List.of());

        assertThat(effects.coefficients()).isEmpty();
        assertThat(effects.agreements()).isEmpty();
        assertThat(effects.communityStart()).isEqualTo(new CommunityStartChange(at("2025-01-01"), at("2025-01-01")));
    }

    // ---- SHA-015 / AC6 ----

    @Test
    @DisplayName("SHA-015 every invalid row is reported, by the preview and the write alike, and nothing is written")
    void refusesEveryInvalidRowAndWritesNothing() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x, y, w);
        Supply withoutRows = createSupply();
        UUID unknown = UUID.randomUUID();
        State before = state();

        CoefficientDatesRequest request = request(
                List.of(date(b, x, "2025-03-01"),
                        date(b, y, "2025-01-01"),
                        date(b, w, "2026-02-01"),
                        new CoefficientDate(unknown, LocalDate.parse("2025-03-01")),
                        new CoefficientDate(a.of(z), LocalDate.parse("2025-03-01"))),
                List.of(closure(x, "2025-03-01"), closure(withoutRows, "2025-03-01")));
        List<CoefficientActivationError> previewErrors = refusedByPreview(b, request);
        List<CoefficientActivationError> writeErrors = refusedByWrite(b, request);

        assertThat(writeErrors).containsExactlyInAnyOrderElementsOf(previewErrors);
        Map<CoefficientActivationErrorCode, List<Map<String, String>>> byCode = writeErrors.stream().collect(
                Collectors.groupingBy(CoefficientActivationError::getCode,
                        Collectors.mapping(CoefficientActivationError::getParams, Collectors.toList())));
        assertThat(byCode.get(CoefficientActivationErrorCode.PERIOD_OVERLAP)).singleElement()
                .satisfies(params -> assertThat(params)
                        .containsEntry("coefficientId", b.of(y).toString())
                        .containsEntry("conflictingCoefficientId", a.of(y).toString()));
        assertThat(byCode.get(CoefficientActivationErrorCode.DATE_IN_FUTURE)).singleElement()
                .satisfies(params -> assertThat(params).containsEntry("coefficientId", b.of(w).toString()));
        assertThat(byCode.get(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT))
                .extracting(params -> params.get("coefficientId"))
                .containsExactlyInAnyOrder(unknown.toString(), a.of(z).toString());
        assertThat(byCode.get(CoefficientActivationErrorCode.LEAVING_SUPPLY_IN_AGREEMENT)).singleElement()
                .satisfies(params -> assertThat(params).containsEntry("supplyId", x.getId().toString())
                        .containsEntry("cups", x.getCode()));
        assertThat(byCode.get(CoefficientActivationErrorCode.NO_COEFFICIENT_TO_CLOSE)).singleElement()
                .satisfies(params -> assertThat(params).containsEntry("supplyId", withoutRows.getId().toString()));
        assertThat(byCode).hasSize(5);
        assertThat(state()).isEqualTo(before);
    }

    @Test
    @DisplayName("SHA-015 a DRAFT agreement is refused before any row is evaluated")
    void refusesADraftAgreement() {
        SharingAgreement draft = createSharingAgreementRepository.create(newAgreement(plant));

        assertThatThrownBy(() -> setService.setDates(plant.getId(), draft.getId(),
                request(List.of(), List.of(closure(z, "2025-03-01")))))
                .isInstanceOf(org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotPublishedException.class);
    }

    // ---- SHA-020 ----

    @Test
    @DisplayName("SHA-020 the preview and the write report a coefficient that drops to 0 as leaving the distribution")
    void dropToZeroLeaves() {
        Map<Supply, String> inA = new LinkedHashMap<>();
        inA.put(x, "0.04");
        inA.put(y, "0.96");
        Agreement a = pendingAgreement(inA);
        activate(a, "2025-01-01", x, y);
        Map<Supply, String> inB = new LinkedHashMap<>();
        inB.put(x, "0");
        inB.put(y, "1");
        Agreement b = pendingAgreement(inB);

        CoefficientDatesEffects effects = previewThenWrite(b,
                List.of(date(b, x, "2025-03-01"), date(b, y, "2025-03-01")), List.of());

        assertThat(effects.coefficients()).containsExactlyInAnyOrder(
                new CoefficientPeriodChange(b.of(x), x.getId(), null, null, at("2025-03-01"), null, true),
                new CoefficientPeriodChange(b.of(y), y.getId(), null, null, at("2025-03-01"), null, false));
        assertThat(effects.neighbours()).containsExactlyInAnyOrder(
                new NeighbourChange(a.of(x), x.getId(), a.id(), null, at("2025-03-01")),
                new NeighbourChange(a.of(y), y.getId(), a.id(), null, at("2025-03-01")));
        assertThat(effects.leavingClosures()).isEmpty();
        assertThat(timeline(x)).containsExactly(period(a.of(x), "2025-01-01", "2025-03-01"),
                period(b.of(x), "2025-03-01", null));
    }

    @Test
    @DisplayName("SHA-020 a supply absent from B is closed as leaving there, and its 0 in C is not a second leaving")
    void zeroAfterAnAbsenceDoesNotLeave() {
        Map<Supply, String> inA = new LinkedHashMap<>();
        inA.put(x, "0.04");
        inA.put(y, "0.96");
        Agreement a = pendingAgreement(inA);
        activate(a, "2025-01-01", x, y);
        Agreement b = pendingAgreement(Map.of(y, "1"));
        Map<Supply, String> inC = new LinkedHashMap<>();
        inC.put(x, "0");
        inC.put(y, "1");
        Agreement c = pendingAgreement(inC);

        CoefficientDatesEffects datingB = previewThenWrite(b, List.of(date(b, y, "2025-02-01")), List.of());
        CoefficientDatesEffects datingC = previewThenWrite(c,
                List.of(date(c, x, "2025-03-01"), date(c, y, "2025-03-01")), List.of());

        assertThat(datingB.leavingClosures()).containsExactly(new LeavingClosureChange(x.getId(), a.of(x), a.id(),
                null, at("2025-02-01"), LeavingClosureSource.DEFAULT));
        assertThat(datingC.coefficients()).containsExactlyInAnyOrder(
                new CoefficientPeriodChange(c.of(x), x.getId(), null, null, at("2025-03-01"), null, false),
                new CoefficientPeriodChange(c.of(y), y.getId(), null, null, at("2025-03-01"), null, false));
    }

    // ---- helpers ----

    /**
     * The preview, on data it must leave as it found, then the write on the same data: both report the same effects,
     * and the write's effects are what the database now holds.
     */
    private CoefficientDatesEffects previewThenWrite(Agreement agreement, List<CoefficientDate> coefficients,
                                                     List<LeavingClosureDate> closures) {
        CoefficientDatesRequest request = request(coefficients, closures);
        State before = state();

        CoefficientDatesEffects previewed = previewService.previewDates(plant.getId(), agreement.id(), request).effects();
        assertThat(state()).as("the preview writes nothing").isEqualTo(before);

        CoefficientDatesEffects written = setService.setDates(plant.getId(), agreement.id(), request).effects();
        assertThat(written).as("the preview announces exactly what the write does").isEqualTo(previewed);
        assertMatchesDatabase(written);
        return written;
    }

    private void assertMatchesDatabase(CoefficientDatesEffects effects) {
        Map<UUID, Row> rows = state().rows();
        effects.coefficients().forEach(change -> assertThat(rows.get(change.coefficientId()))
                .isEqualTo(new Row(change.validFromAfter(), change.validToAfter())));
        effects.neighbours().forEach(change -> assertThat(rows.get(change.coefficientId()).validTo())
                .isEqualTo(change.validToAfter()));
        effects.leavingClosures().forEach(change -> assertThat(rows.get(change.coefficientId()).validTo())
                .isEqualTo(change.validToAfter()));
        effects.agreements().forEach(change -> assertThat(getSharingAgreementRepository
                .findById(change.sharingAgreementId()).orElseThrow().getStatus()).isEqualTo(change.statusAfter()));
        assertThat(getCoefficientRepository.findEarliestValidFromByCommunityId(community.getId()).orElse(null))
                .isEqualTo(effects.communityStart().after());
    }

    private List<CoefficientActivationError> refusedByPreview(Agreement agreement, CoefficientDatesRequest request) {
        return catchThrowableOfType(() -> previewService.previewDates(plant.getId(), agreement.id(), request),
                CoefficientActivationException.class).getErrors();
    }

    private List<CoefficientActivationError> refusedByWrite(Agreement agreement, CoefficientDatesRequest request) {
        return catchThrowableOfType(() -> setService.setDates(plant.getId(), agreement.id(), request),
                CoefficientActivationException.class).getErrors();
    }

    private record Row(Instant validFrom, Instant validTo) {
    }

    /**
     * Every coefficient row of the plant, every agreement's status and the community start.
     */
    private record State(Map<UUID, Row> rows, Map<UUID, SharingAgreementStatus> statuses,
                         Optional<Instant> communityStart) {
    }

    private State state() {
        Map<UUID, Row> rows = new LinkedHashMap<>();
        getCoefficientRepository.findAllByPlantId(plant.getId()).stream()
                .sorted(Comparator.comparing(row -> row.getId().toString()))
                .forEach(row -> rows.put(row.getId(), new Row(row.getValidFrom(), row.getValidTo())));
        Map<UUID, SharingAgreementStatus> statuses = getSharingAgreementRepository.findByPlantId(plant.getId(), null)
                .stream().collect(Collectors.toMap(SharingAgreement::getId, SharingAgreement::getStatus));
        return new State(rows, statuses, getCoefficientRepository.findEarliestValidFromByCommunityId(community.getId()));
    }

    private record Agreement(UUID id, Map<UUID, UUID> coefficientIdBySupplyId) {
        UUID of(Supply supply) {
            return coefficientIdBySupplyId.get(supply.getId());
        }
    }

    private record Period(UUID id, Instant validFrom, Instant validTo) {
    }

    private Period period(UUID coefficientId, String validFrom, String validTo) {
        return new Period(coefficientId, at(validFrom), validTo == null ? null : at(validTo));
    }

    private List<Period> timeline(Supply supply) {
        return getCoefficientRepository.findAllByPlantIdAndSupplyIdIn(plant.getId(), List.of(supply.getId())).stream()
                .filter(coefficient -> coefficient.getValidFrom() != null)
                .sorted(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom))
                .map(coefficient -> new Period(coefficient.getId(), coefficient.getValidFrom(), coefficient.getValidTo()))
                .toList();
    }

    private Instant at(String date) {
        return LocalDate.parse(date).atStartOfDay(zoneResolver.resolveApplicationZoneId()).toInstant();
    }

    private SharingAgreementStatus statusOf(Agreement agreement) {
        return getSharingAgreementRepository.findById(agreement.id()).orElseThrow().getStatus();
    }

    private static CoefficientDatesRequest request(List<CoefficientDate> coefficients,
                                                   List<LeavingClosureDate> closures) {
        return new CoefficientDatesRequest(coefficients, closures);
    }

    private static CoefficientDate date(Agreement agreement, Supply supply, String validFrom) {
        return new CoefficientDate(agreement.of(supply), LocalDate.parse(validFrom));
    }

    private static LeavingClosureDate closure(Supply supply, String closedOn) {
        return new LeavingClosureDate(supply.getId(), LocalDate.parse(closedOn));
    }

    private Agreement activatedAgreement(String appliedOn, Supply... supplies) {
        Agreement agreement = pendingAgreement(supplies);
        activate(agreement, appliedOn, supplies);
        return agreement;
    }

    /**
     * A new published agreement with a pending coefficient of the given value for each supply.
     */
    private Agreement pendingAgreement(Map<Supply, String> values) {
        Map<Supply, BigDecimal> coefficients = new LinkedHashMap<>();
        values.forEach((supply, value) -> coefficients.put(supply, new BigDecimal(value)));
        return pendingAgreement(plant, coefficients);
    }

    private Agreement pendingAgreement(Supply... supplies) {
        return pendingAgreement(plant, supplies);
    }

    /**
     * A new published agreement with a pending coefficient for each supply, exactly as materialisation writes them.
     */
    private Agreement pendingAgreement(Plant owner, Supply... supplies) {
        Map<Supply, BigDecimal> coefficients = new LinkedHashMap<>();
        Arrays.stream(supplies).forEach(supply -> coefficients.put(supply, BigDecimal.ONE));
        return pendingAgreement(owner, coefficients);
    }

    private Agreement pendingAgreement(Plant owner, Map<Supply, BigDecimal> coefficients) {
        SharingAgreement draft = createSharingAgreementRepository.create(newAgreement(owner));
        SharingAgreement published = publishSharingAgreementRepository.publish(owner.getId(), draft.getId());
        Map<UUID, UUID> coefficientIdBySupplyId = new LinkedHashMap<>();
        for (Map.Entry<Supply, BigDecimal> entry : coefficients.entrySet()) {
            Supply supply = entry.getKey();
            SupplyPartitionCoefficient saved = saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                    .withId(UUID.randomUUID())
                    .withSupplyId(supply.getId())
                    .withPlantId(owner.getId())
                    .withSharingAgreementId(published.getId())
                    .withCoefficient(entry.getValue())
                    .withValidFrom(null)
                    .withValidTo(null)
                    .withCreatedAt(Instant.now())
                    .build());
            coefficientIdBySupplyId.put(supply.getId(), saved.getId());
        }
        return new Agreement(published.getId(), coefficientIdBySupplyId);
    }

    private static SharingAgreement newAgreement(Plant owner) {
        return new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(owner.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build();
    }

    private void activate(Agreement agreement, String appliedOn, Supply... supplies) {
        activationService.setValidFrom(plant.getId(), agreement.id(), LocalDate.parse(appliedOn),
                Arrays.stream(supplies).map(agreement::of).toList());
    }

    private void reopen(Agreement agreement, Supply supply) {
        activationService.setValidTo(plant.getId(), agreement.id(), null, List.of(agreement.of(supply)));
    }

    private Plant createPlant() {
        Supply ownSupply = createSupply();
        return createPlantRepository.create(PlantMother.random(ownSupply).build(), SupplyId.of(ownSupply.getId()));
    }

    private Supply createSupply() {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }
}
