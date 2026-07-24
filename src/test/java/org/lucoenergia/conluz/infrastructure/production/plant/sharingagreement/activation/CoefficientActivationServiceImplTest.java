package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.activation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.RecomputeSharingAgreementStatusRepository;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotPublishedException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementPlantMismatchException;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoefficientActivationServiceImplTest {

    @Mock
    private GetSharingAgreementService getSharingAgreementService;
    @Mock
    private GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    @Mock
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Mock
    private RecomputeSharingAgreementStatusRepository recomputeStatusRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private ZoneResolver zoneResolver;

    private CoefficientActivationServiceImpl service() {
        return new CoefficientActivationServiceImpl(getSharingAgreementService, getCoefficientRepository,
                saveCoefficientRepository, recomputeStatusRepository, getSupplyRepository, zoneResolver);
    }

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final ZoneId UTC = ZoneOffset.UTC;

    private SharingAgreement agreement(UUID id, SharingAgreementStatus status) {
        return new SharingAgreement.Builder().withId(id).withPlantId(PLANT_ID).withStatus(status).build();
    }

    private SupplyPartitionCoefficient coefficient(UUID id, UUID agreementId, UUID supplyId,
                                                     Instant validFrom, Instant validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(id)
                .withSupplyId(supplyId)
                .withPlantId(PLANT_ID)
                .withSharingAgreementId(agreementId)
                .withCoefficient(java.math.BigDecimal.ONE)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.EPOCH)
                .build();
    }

    private void stubFound(UUID agreementId, SupplyPartitionCoefficient... coefficients) {
        when(getCoefficientRepository.findAllByIdAndSharingAgreementId(anyList(), eq(agreementId)))
                .thenReturn(List.of(coefficients));
    }

    private void stubSave() {
        when(saveCoefficientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---- agreement-status guard ----

    @Test
    void setValidFrom_throwsNotPublished_whenAgreementIsDraft() {
        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.DRAFT));

        assertThrows(SharingAgreementNotPublishedException.class,
                () -> service().setValidFrom(PLANT_ID, agreementId, LocalDate.now(), List.of(UUID.randomUUID())));
        verify(getCoefficientRepository, never()).findAllByIdAndSharingAgreementId(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SharingAgreementStatus.class, names = {"PUBLISHED", "SUPERSEDED"})
    void setValidFrom_doesNotThrow_whenAgreementIsPublishedOrSuperseded(SharingAgreementStatus status) {
        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, status));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId);

        List<SupplyPartitionCoefficient> result = service().setValidFrom(PLANT_ID, agreementId, LocalDate.now(), List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void setValidFrom_throwsPlantMismatch_whenAgreementBelongsToAnotherPlant() {
        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement.Builder().withId(agreementId)
                .withPlantId(UUID.randomUUID()).withStatus(SharingAgreementStatus.PUBLISHED).build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement);

        assertThrows(SharingAgreementPlantMismatchException.class,
                () -> service().setValidFrom(PLANT_ID, agreementId, LocalDate.now(), List.of(UUID.randomUUID())));
    }

    // ---- setValidFrom: activate / correct ----

    @Test
    void setValidFrom_activatesPending_whenNoPredecessorExists() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient pending = coefficient(coefficientId, agreementId, supplyId, null, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, pending);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, null)).thenReturn(Optional.empty());
        stubSave();

        LocalDate appliedOn = LocalDate.now(UTC).minusDays(10);
        List<SupplyPartitionCoefficient> result = service().setValidFrom(PLANT_ID, agreementId, appliedOn, List.of(coefficientId));

        assertEquals(1, result.size());
        assertEquals(appliedOn.atStartOfDay(UTC).toInstant(), result.get(0).getValidFrom());
        verify(recomputeStatusRepository, times(1)).recomputeStatus(agreementId);
    }

    @Test
    void setValidFrom_activatesAndClosesPredecessor_whenOpenPredecessorExists() {
        UUID targetAgreementId = UUID.randomUUID();
        UUID predecessorAgreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient pending = coefficient(coefficientId, targetAgreementId, supplyId, null, null);
        Instant predecessorValidFrom = Instant.EPOCH;
        SupplyPartitionCoefficient predecessor = coefficient(predecessorId, predecessorAgreementId, supplyId, predecessorValidFrom, null);

        when(getSharingAgreementService.findById(targetAgreementId)).thenReturn(agreement(targetAgreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(targetAgreementId, pending);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, null)).thenReturn(Optional.of(predecessor));
        stubSave();

        LocalDate appliedOn = LocalDate.now(UTC).minusDays(1);
        Instant newValidFrom = appliedOn.atStartOfDay(UTC).toInstant();
        List<SupplyPartitionCoefficient> result = service().setValidFrom(PLANT_ID, targetAgreementId, appliedOn, List.of(coefficientId));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(coefficientId) && newValidFrom.equals(c.getValidFrom())));
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(predecessorId) && newValidFrom.equals(c.getValidTo())));
        verify(recomputeStatusRepository, times(1)).recomputeStatus(targetAgreementId);
        verify(recomputeStatusRepository, times(1)).recomputeStatus(predecessorAgreementId);
    }

    @Test
    void setValidFrom_correctsDate_movesPredecessorValidToLater() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant originalValidFrom = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient active = coefficient(coefficientId, agreementId, supplyId, originalValidFrom, null);
        Instant predecessorValidFrom = LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient predecessor = coefficient(predecessorId, agreementId, supplyId, predecessorValidFrom, originalValidFrom);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, active);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, originalValidFrom))
                .thenReturn(Optional.of(predecessor));
        stubSave();

        LocalDate corrected = LocalDate.now(UTC).minusDays(5);
        Instant newValidFrom = corrected.atStartOfDay(UTC).toInstant();
        List<SupplyPartitionCoefficient> result = service().setValidFrom(PLANT_ID, agreementId, corrected, List.of(coefficientId));

        assertTrue(result.stream().anyMatch(c -> c.getId().equals(predecessorId) && newValidFrom.equals(c.getValidTo())));
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(coefficientId) && newValidFrom.equals(c.getValidFrom())));
    }

    @Test
    void setValidFrom_correctsDate_worksOnAlreadySupersededCoefficient() {
        // The predecessor lookup uses the coefficient's OWN current validFrom as the boundary,
        // regardless of whether this coefficient is itself still open (validTo == null) or already
        // closed by a later cascade -- proving correction isn't gated on "is this the open row".
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant originalValidFrom = LocalDate.now(UTC).minusDays(20).atStartOfDay(UTC).toInstant();
        Instant ownValidTo = LocalDate.now(UTC).minusDays(5).atStartOfDay(UTC).toInstant(); // closed by a later cascade
        SupplyPartitionCoefficient superseded = coefficient(coefficientId, agreementId, supplyId, originalValidFrom, ownValidTo);
        SupplyPartitionCoefficient predecessor = coefficient(predecessorId, agreementId, supplyId,
                LocalDate.now(UTC).minusDays(40).atStartOfDay(UTC).toInstant(), originalValidFrom);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, superseded);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, originalValidFrom))
                .thenReturn(Optional.of(predecessor));
        stubSave();

        LocalDate corrected = LocalDate.now(UTC).minusDays(15);
        service().setValidFrom(PLANT_ID, agreementId, corrected, List.of(coefficientId));

        verify(getCoefficientRepository).findPredecessor(PLANT_ID, supplyId, coefficientId, originalValidFrom);
    }

    @Test
    void setValidFrom_rejectsCorrection_whenNewDateEqualsPredecessorValidFrom() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant predecessorValidFrom = LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant();
        Instant originalValidFrom = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient active = coefficient(coefficientId, agreementId, supplyId, originalValidFrom, null);
        SupplyPartitionCoefficient predecessor = coefficient(predecessorId, agreementId, supplyId, predecessorValidFrom, originalValidFrom);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, active);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, originalValidFrom))
                .thenReturn(Optional.of(predecessor));

        LocalDate emptyRangeDate = predecessorValidFrom.atZone(UTC).toLocalDate(); // D' == predecessor.validFrom
        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidFrom(PLANT_ID, agreementId, emptyRangeDate, List.of(coefficientId)));

        assertEquals(1, exception.getErrors().size());
        assertEquals(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_AFTER_PREDECESSOR, exception.getErrors().get(0).getCode());
        verify(saveCoefficientRepository, never()).save(any());
        verify(recomputeStatusRepository, never()).recomputeStatus(any());
    }

    @Test
    void setValidFrom_rejectsCorrection_whenNewDateBeforePredecessorValidFrom() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant predecessorValidFrom = LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant();
        Instant originalValidFrom = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient active = coefficient(coefficientId, agreementId, supplyId, originalValidFrom, null);
        SupplyPartitionCoefficient predecessor = coefficient(predecessorId, agreementId, supplyId, predecessorValidFrom, originalValidFrom);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, active);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, originalValidFrom))
                .thenReturn(Optional.of(predecessor));

        LocalDate beforePredecessor = predecessorValidFrom.atZone(UTC).toLocalDate().minusDays(1);
        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidFrom(PLANT_ID, agreementId, beforePredecessor, List.of(coefficientId)));

        assertEquals(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_AFTER_PREDECESSOR, exception.getErrors().get(0).getCode());
    }

    @Test
    void setValidFrom_rejectsCorrection_whenNewDateAtOrAfterOwnValidTo() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant originalValidFrom = LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant();
        Instant ownValidTo = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient active = coefficient(coefficientId, agreementId, supplyId, originalValidFrom, ownValidTo);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, active);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, originalValidFrom))
                .thenReturn(Optional.empty());

        LocalDate atOwnValidTo = ownValidTo.atZone(UTC).toLocalDate();
        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidFrom(PLANT_ID, agreementId, atOwnValidTo, List.of(coefficientId)));

        assertEquals(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR, exception.getErrors().get(0).getCode());
    }

    // ---- setValidFrom: revert to pending ----

    @Test
    void setValidFrom_revertsToPending_splicesPredecessorValidToOntoOwnOldValidTo() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant ownValidFrom = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        Instant ownValidTo = LocalDate.now(UTC).minusDays(2).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient active = coefficient(coefficientId, agreementId, supplyId, ownValidFrom, ownValidTo);
        SupplyPartitionCoefficient predecessor = coefficient(predecessorId, agreementId, supplyId,
                LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant(), ownValidFrom);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, active);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, ownValidFrom))
                .thenReturn(Optional.of(predecessor));
        stubSave();

        List<SupplyPartitionCoefficient> result = service().setValidFrom(PLANT_ID, agreementId, null, List.of(coefficientId));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(coefficientId) && c.getValidFrom() == null && c.getValidTo() == null));
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(predecessorId) && ownValidTo.equals(c.getValidTo())));
    }

    @Test
    void setValidFrom_revertsToPending_predecessorReopensToInfinity_whenOwnValidToWasNull() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant ownValidFrom = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient active = coefficient(coefficientId, agreementId, supplyId, ownValidFrom, null);
        SupplyPartitionCoefficient predecessor = coefficient(predecessorId, agreementId, supplyId,
                LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant(), ownValidFrom);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, active);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, ownValidFrom))
                .thenReturn(Optional.of(predecessor));
        stubSave();

        List<SupplyPartitionCoefficient> result = service().setValidFrom(PLANT_ID, agreementId, null, List.of(coefficientId));

        assertTrue(result.stream().anyMatch(c -> c.getId().equals(predecessorId) && c.getValidTo() == null));
    }

    @Test
    void setValidFrom_revertsToPending_leavesGap_whenNoPredecessorAndSuccessorExists() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant ownValidFrom = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        Instant ownValidTo = LocalDate.now(UTC).minusDays(2).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient active = coefficient(coefficientId, agreementId, supplyId, ownValidFrom, ownValidTo);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, active);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, ownValidFrom))
                .thenReturn(Optional.empty());
        stubSave();

        List<SupplyPartitionCoefficient> result = service().setValidFrom(PLANT_ID, agreementId, null, List.of(coefficientId));

        assertEquals(1, result.size());
        verify(saveCoefficientRepository, times(1)).save(any());
        verify(recomputeStatusRepository, times(1)).recomputeStatus(agreementId);
    }

    // ---- setValidFrom: future date / no-op / accumulation ----

    @Test
    void setValidFrom_rejectsFutureDate_evaluatedInResolvedZone() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient pending = coefficient(coefficientId, agreementId, supplyId, null, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, pending);

        LocalDate tomorrow = LocalDate.now(UTC).plusDays(1);
        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidFrom(PLANT_ID, agreementId, tomorrow, List.of(coefficientId)));

        assertEquals(CoefficientActivationErrorCode.DATE_IN_FUTURE, exception.getErrors().get(0).getCode());
        verify(saveCoefficientRepository, never()).save(any());
    }

    @Test
    void setValidFrom_isNoOp_whenRequestedDateAlreadyPersisted() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        LocalDate appliedOn = LocalDate.now(UTC).minusDays(5);
        Instant validFrom = appliedOn.atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient active = coefficient(coefficientId, agreementId, supplyId, validFrom, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, active);

        List<SupplyPartitionCoefficient> result = service().setValidFrom(PLANT_ID, agreementId, appliedOn, List.of(coefficientId));

        assertTrue(result.isEmpty());
        verify(saveCoefficientRepository, never()).save(any());
        verify(recomputeStatusRepository, never()).recomputeStatus(any());
        verify(getCoefficientRepository, never()).findPredecessor(any(), any(), any(), any());
    }

    @Test
    void setValidFrom_rejectsUnknownOrForeignCoefficientId() {
        UUID agreementId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId); // empty -- the id belongs to no coefficient of this agreement

        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidFrom(PLANT_ID, agreementId, LocalDate.now(UTC).minusDays(1), List.of(unknownId)));

        assertEquals(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT, exception.getErrors().get(0).getCode());
    }

    @Test
    void setValidFrom_accumulatesAllErrors_acrossMixedBatch_writesNothing() {
        UUID agreementId = UUID.randomUUID();
        UUID validId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient pending = coefficient(validId, agreementId, supplyId, null, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, pending);
        when(getCoefficientRepository.findPredecessor(eq(PLANT_ID), eq(supplyId), eq(validId), any()))
                .thenReturn(Optional.empty());

        LocalDate appliedOn = LocalDate.now(UTC).minusDays(1);
        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidFrom(PLANT_ID, agreementId, appliedOn, List.of(validId, unknownId)));

        assertEquals(1, exception.getErrors().size());
        assertEquals(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT, exception.getErrors().get(0).getCode());
        // The would-be-valid item's write must not have happened either -- the whole batch rolls back.
        verify(saveCoefficientRepository, never()).save(any());
        verify(recomputeStatusRepository, never()).recomputeStatus(any());
    }

    // ---- setValidTo: close / correct-close / reopen ----

    @Test
    void setValidTo_closesOpenCoefficient_whenNoSuccessorExists() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validFrom = LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient open = coefficient(coefficientId, agreementId, supplyId, validFrom, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, open);
        stubSave();

        LocalDate closedOn = LocalDate.now(UTC).minusDays(1);
        List<SupplyPartitionCoefficient> result = service().setValidTo(PLANT_ID, agreementId, closedOn, List.of(coefficientId));

        assertEquals(1, result.size());
        assertEquals(closedOn.atStartOfDay(UTC).toInstant(), result.get(0).getValidTo());
        verify(getCoefficientRepository, never()).findNextActivatedAfter(any(), any(), any(), any());
    }

    @Test
    void setValidTo_rejectsClose_whenCoefficientIsStillPending() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient pending = coefficient(coefficientId, agreementId, supplyId, null, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, pending);

        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidTo(PLANT_ID, agreementId, LocalDate.now(UTC).minusDays(1), List.of(coefficientId)));

        assertEquals(CoefficientActivationErrorCode.COEFFICIENT_NOT_ACTIVE, exception.getErrors().get(0).getCode());
        verify(saveCoefficientRepository, never()).save(any());
    }

    @Test
    void setValidTo_rejectsClose_whenDateNotAfterOwnValidFrom() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        LocalDate validFromDate = LocalDate.now(UTC).minusDays(10);
        Instant validFrom = validFromDate.atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient open = coefficient(coefficientId, agreementId, supplyId, validFrom, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, open);

        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidTo(PLANT_ID, agreementId, validFromDate, List.of(coefficientId)));

        assertEquals(CoefficientActivationErrorCode.CLOSURE_DATE_NOT_AFTER_ACTIVATION, exception.getErrors().get(0).getCode());
    }

    @Test
    void setValidTo_correctsCloseDate_whenNoSuccessorStartsAtCurrentValidTo() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validFrom = LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant();
        Instant currentValidTo = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient closed = coefficient(coefficientId, agreementId, supplyId, validFrom, currentValidTo);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, closed);
        when(getCoefficientRepository.findNextActivatedAfter(PLANT_ID, supplyId, coefficientId, validFrom))
                .thenReturn(Optional.empty());
        stubSave();

        LocalDate corrected = LocalDate.now(UTC).minusDays(5);
        List<SupplyPartitionCoefficient> result = service().setValidTo(PLANT_ID, agreementId, corrected, List.of(coefficientId));

        assertEquals(corrected.atStartOfDay(UTC).toInstant(), result.get(0).getValidTo());
    }

    @Test
    void setValidTo_rejectsCorrection_whenSuccessorStartsExactlyAtCurrentValidTo() {
        // Cascade-derived boundary: correcting it here would desynchronize it from the successor
        // that actually derives it.
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validFrom = LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant();
        Instant currentValidTo = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient closed = coefficient(coefficientId, agreementId, supplyId, validFrom, currentValidTo);
        SupplyPartitionCoefficient successor = coefficient(successorId, agreementId, supplyId, currentValidTo, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, closed);
        when(getCoefficientRepository.findNextActivatedAfter(PLANT_ID, supplyId, coefficientId, validFrom))
                .thenReturn(Optional.of(successor));

        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidTo(PLANT_ID, agreementId, LocalDate.now(UTC).minusDays(5), List.of(coefficientId)));

        assertEquals(CoefficientActivationErrorCode.COEFFICIENT_HAS_SUCCESSOR, exception.getErrors().get(0).getCode());
        verify(saveCoefficientRepository, never()).save(any());
    }

    @Test
    void setValidTo_rejectsCorrection_whenRequestedValueReachesNonAdjacentSuccessor() {
        // The current validTo is self-authored (not adjacent to the next row), but the REQUESTED new
        // value would still reach/overlap that next, non-adjacent row -- must be rejected regardless.
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validFrom = LocalDate.now(UTC).minusDays(60).atStartOfDay(UTC).toInstant();
        Instant currentValidTo = LocalDate.now(UTC).minusDays(40).atStartOfDay(UTC).toInstant(); // self-authored, exit case
        Instant nextValidFrom = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant(); // unrelated, later, non-adjacent
        SupplyPartitionCoefficient closed = coefficient(coefficientId, agreementId, supplyId, validFrom, currentValidTo);
        SupplyPartitionCoefficient next = coefficient(nextId, UUID.randomUUID(), supplyId, nextValidFrom, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, closed);
        when(getCoefficientRepository.findNextActivatedAfter(PLANT_ID, supplyId, coefficientId, validFrom))
                .thenReturn(Optional.of(next));

        LocalDate pastNext = nextValidFrom.atZone(UTC).toLocalDate().plusDays(1);
        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidTo(PLANT_ID, agreementId, pastNext, List.of(coefficientId)));

        assertEquals(CoefficientActivationErrorCode.COEFFICIENT_HAS_SUCCESSOR, exception.getErrors().get(0).getCode());
    }

    @Test
    void setValidTo_correctsUpToNonAdjacentSuccessorBoundary_whenNotReachingIt() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validFrom = LocalDate.now(UTC).minusDays(60).atStartOfDay(UTC).toInstant();
        Instant currentValidTo = LocalDate.now(UTC).minusDays(40).atStartOfDay(UTC).toInstant();
        Instant nextValidFrom = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient closed = coefficient(coefficientId, agreementId, supplyId, validFrom, currentValidTo);
        SupplyPartitionCoefficient next = coefficient(nextId, UUID.randomUUID(), supplyId, nextValidFrom, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, closed);
        when(getCoefficientRepository.findNextActivatedAfter(PLANT_ID, supplyId, coefficientId, validFrom))
                .thenReturn(Optional.of(next));
        stubSave();

        LocalDate withinGap = LocalDate.now(UTC).minusDays(20); // strictly between currentValidTo and nextValidFrom
        List<SupplyPartitionCoefficient> result = service().setValidTo(PLANT_ID, agreementId, withinGap, List.of(coefficientId));

        assertEquals(withinGap.atStartOfDay(UTC).toInstant(), result.get(0).getValidTo());
    }

    @Test
    void setValidTo_reopens_whenNoSuccessorStartsAtValidTo() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validFrom = LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant();
        Instant currentValidTo = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient closed = coefficient(coefficientId, agreementId, supplyId, validFrom, currentValidTo);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, closed);
        when(getCoefficientRepository.findNextActivatedAfter(PLANT_ID, supplyId, coefficientId, validFrom))
                .thenReturn(Optional.empty());
        stubSave();

        List<SupplyPartitionCoefficient> result = service().setValidTo(PLANT_ID, agreementId, null, List.of(coefficientId));

        assertEquals(1, result.size());
        assertEquals(null, result.get(0).getValidTo());
    }

    @Test
    void setValidTo_rejectsReopen_whenAnySuccessorExistsAfterValidFrom() {
        // Reopening extends validTo to infinity, so ANY later activated row -- however far -- blocks it.
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validFrom = LocalDate.now(UTC).minusDays(60).atStartOfDay(UTC).toInstant();
        Instant currentValidTo = LocalDate.now(UTC).minusDays(40).atStartOfDay(UTC).toInstant();
        Instant nextValidFrom = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient closed = coefficient(coefficientId, agreementId, supplyId, validFrom, currentValidTo);
        SupplyPartitionCoefficient next = coefficient(nextId, UUID.randomUUID(), supplyId, nextValidFrom, null);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, closed);
        when(getCoefficientRepository.findNextActivatedAfter(PLANT_ID, supplyId, coefficientId, validFrom))
                .thenReturn(Optional.of(next));

        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service().setValidTo(PLANT_ID, agreementId, null, List.of(coefficientId)));

        assertEquals(CoefficientActivationErrorCode.COEFFICIENT_HAS_SUCCESSOR, exception.getErrors().get(0).getCode());
    }

    @Test
    void setValidTo_isNoOp_whenRequestedValueAlreadyPersisted() {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validFrom = LocalDate.now(UTC).minusDays(30).atStartOfDay(UTC).toInstant();
        Instant validTo = LocalDate.now(UTC).minusDays(10).atStartOfDay(UTC).toInstant();
        SupplyPartitionCoefficient closed = coefficient(coefficientId, agreementId, supplyId, validFrom, validTo);

        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(agreementId, closed);

        LocalDate sameDate = validTo.atZone(UTC).toLocalDate();
        List<SupplyPartitionCoefficient> result = service().setValidTo(PLANT_ID, agreementId, sameDate, List.of(coefficientId));

        assertTrue(result.isEmpty());
        verify(saveCoefficientRepository, never()).save(any());
        verify(getCoefficientRepository, never()).findNextActivatedAfter(any(), any(), any(), any());
    }

    // ---- recompute cost ----

    @Test
    void recomputeCalledOnce_perDistinctAffectedAgreement_notPerCoefficient() {
        UUID targetAgreementId = UUID.randomUUID();
        UUID predecessorAgreementA = UUID.randomUUID();
        UUID predecessorAgreementB = UUID.randomUUID();

        UUID coefficient1 = UUID.randomUUID();
        UUID supply1 = UUID.randomUUID();
        UUID predecessor1 = UUID.randomUUID();
        UUID coefficient2 = UUID.randomUUID();
        UUID supply2 = UUID.randomUUID();
        UUID predecessor2 = UUID.randomUUID();
        UUID coefficient3 = UUID.randomUUID();
        UUID supply3 = UUID.randomUUID();

        SupplyPartitionCoefficient c1 = coefficient(coefficient1, targetAgreementId, supply1, null, null);
        SupplyPartitionCoefficient c2 = coefficient(coefficient2, targetAgreementId, supply2, null, null);
        SupplyPartitionCoefficient c3 = coefficient(coefficient3, targetAgreementId, supply3, null, null);
        SupplyPartitionCoefficient p1 = coefficient(predecessor1, predecessorAgreementA, supply1, Instant.EPOCH, null);
        SupplyPartitionCoefficient p2 = coefficient(predecessor2, predecessorAgreementB, supply2, Instant.EPOCH, null);

        when(getSharingAgreementService.findById(targetAgreementId)).thenReturn(agreement(targetAgreementId, SharingAgreementStatus.PUBLISHED));
        when(zoneResolver.resolveZoneId(PLANT_ID)).thenReturn(UTC);
        stubFound(targetAgreementId, c1, c2, c3);
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supply1, coefficient1, null)).thenReturn(Optional.of(p1));
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supply2, coefficient2, null)).thenReturn(Optional.of(p2));
        when(getCoefficientRepository.findPredecessor(PLANT_ID, supply3, coefficient3, null)).thenReturn(Optional.empty());
        stubSave();

        LocalDate appliedOn = LocalDate.now(UTC).minusDays(1);
        service().setValidFrom(PLANT_ID, targetAgreementId, appliedOn, List.of(coefficient1, coefficient2, coefficient3));

        verify(recomputeStatusRepository, times(1)).recomputeStatus(targetAgreementId);
        verify(recomputeStatusRepository, times(1)).recomputeStatus(predecessorAgreementA);
        verify(recomputeStatusRepository, times(1)).recomputeStatus(predecessorAgreementB);
        verifyNoMoreInteractions(recomputeStatusRepository);
    }

    // ---- zone / DST ----

    @ParameterizedTest
    @ValueSource(strings = {"Europe/Madrid", "Pacific/Auckland"})
    void setValidFrom_convertsThroughResolvedZone_persistedInstantsDifferByDstOffset(String zoneIdValue) {
        ZoneId zoneId = ZoneId.of(zoneIdValue);
        ZoneRules rules = zoneId.getRules();
        // A past transition, deliberately -- appliedOn must never be in the future (D5), so both
        // fixture dates below need to already be behind "today".
        ZoneOffsetTransition transition = rules.previousTransition(Instant.now());
        assertNotEquals(null, transition, "test zone must have at least one past DST transition");
        LocalDate dayBeforeTransition = transition.getDateTimeBefore().toLocalDate().minusDays(1);
        LocalDate dayAfterTransition = transition.getDateTimeAfter().toLocalDate().plusDays(1);

        Instant instantBefore = activateAndCaptureValidFrom(zoneId, dayBeforeTransition);
        Instant instantAfter = activateAndCaptureValidFrom(zoneId, dayAfterTransition);

        Duration actualGap = Duration.between(instantBefore, instantAfter);
        Duration daysAsFixedOffset = Duration.between(dayBeforeTransition.atStartOfDay(ZoneOffset.UTC),
                dayAfterTransition.atStartOfDay(ZoneOffset.UTC));

        // A fixed-offset (or UTC-midnight) conversion would always produce exactly this many whole
        // days; the zone-id-based conversion must differ by the DST adjustment.
        assertNotEquals(daysAsFixedOffset, actualGap);
    }

    private Instant activateAndCaptureValidFrom(ZoneId zoneId, LocalDate appliedOn) {
        UUID agreementId = UUID.randomUUID();
        UUID coefficientId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient pending = coefficient(coefficientId, agreementId, supplyId, null, null);

        GetSharingAgreementService agreementService = org.mockito.Mockito.mock(GetSharingAgreementService.class);
        GetSupplyPartitionCoefficientRepository coefficientRepository = org.mockito.Mockito.mock(GetSupplyPartitionCoefficientRepository.class);
        SaveSupplyPartitionCoefficientRepository saveRepository = org.mockito.Mockito.mock(SaveSupplyPartitionCoefficientRepository.class);
        ZoneResolver resolver = org.mockito.Mockito.mock(ZoneResolver.class);

        when(agreementService.findById(agreementId)).thenReturn(agreement(agreementId, SharingAgreementStatus.PUBLISHED));
        when(resolver.resolveZoneId(PLANT_ID)).thenReturn(zoneId);
        when(coefficientRepository.findAllByIdAndSharingAgreementId(anyList(), eq(agreementId))).thenReturn(List.of(pending));
        when(coefficientRepository.findPredecessor(PLANT_ID, supplyId, coefficientId, null)).thenReturn(Optional.empty());
        ArgumentCaptor<SupplyPartitionCoefficient> captor = ArgumentCaptor.forClass(SupplyPartitionCoefficient.class);
        when(saveRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        CoefficientActivationServiceImpl serviceUnderTest = new CoefficientActivationServiceImpl(
                agreementService, coefficientRepository, saveRepository, recomputeStatusRepository, getSupplyRepository, resolver);
        serviceUnderTest.setValidFrom(PLANT_ID, agreementId, appliedOn, List.of(coefficientId));

        return captor.getValue().getValidFrom();
    }
}
