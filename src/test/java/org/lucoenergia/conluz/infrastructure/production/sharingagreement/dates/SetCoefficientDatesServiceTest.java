package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientOverlapCheckRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.lock.LockPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.RecomputeSharingAgreementStatusRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationError;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationException;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesRequest;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetCoefficientDatesServiceTest {

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID AGREEMENT_ID = UUID.randomUUID();
    private static final CoefficientDatesRequest REQUEST = new CoefficientDatesRequest(List.of(), List.of());

    @Mock
    private LockPlantRepository lockPlantRepository;
    @Mock
    private CoefficientDatesPlanner planner;
    @Mock
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Mock
    private CoefficientOverlapCheckRepository overlapCheckRepository;
    @Mock
    private RecomputeSharingAgreementStatusRepository recomputeStatusRepository;
    @Mock
    private GetSharingAgreementRepository getSharingAgreementRepository;
    @Mock
    private GetSupplyPartitionCoefficientRepository getCoefficientRepository;

    private SetCoefficientDatesServiceImpl service() {
        return new SetCoefficientDatesServiceImpl(lockPlantRepository, planner, saveCoefficientRepository,
                overlapCheckRepository, recomputeStatusRepository, getSharingAgreementRepository,
                getCoefficientRepository);
    }

    @Test
    @DisplayName("SHA-007 the write locks the plant before it plans")
    void locksThePlantBeforePlanning() {
        when(lockPlantRepository.lockForCoefficientDates(PlantId.of(PLANT_ID))).thenReturn(true);
        when(planner.plan(PLANT_ID, AGREEMENT_ID, REQUEST)).thenThrow(new CoefficientActivationException(List.of(
                new CoefficientActivationError(CoefficientActivationErrorCode.NO_COEFFICIENT_TO_CLOSE, Map.of()))));

        assertThatThrownBy(() -> service().setDates(PLANT_ID, AGREEMENT_ID, REQUEST))
                .isInstanceOf(CoefficientActivationException.class);

        InOrder inOrder = inOrder(lockPlantRepository, planner);
        inOrder.verify(lockPlantRepository).lockForCoefficientDates(PlantId.of(PLANT_ID));
        inOrder.verify(planner).plan(PLANT_ID, AGREEMENT_ID, REQUEST);
    }

    @Test
    @DisplayName("SHA-015 a refused request writes nothing and recomputes nothing")
    void aRefusedRequestWritesNothing() {
        when(lockPlantRepository.lockForCoefficientDates(PlantId.of(PLANT_ID))).thenReturn(true);
        when(planner.plan(PLANT_ID, AGREEMENT_ID, REQUEST)).thenThrow(new CoefficientActivationException(List.of(
                new CoefficientActivationError(CoefficientActivationErrorCode.PERIOD_OVERLAP, Map.of()))));

        assertThatThrownBy(() -> service().setDates(PLANT_ID, AGREEMENT_ID, REQUEST))
                .isInstanceOf(CoefficientActivationException.class);

        verifyNoInteractions(saveCoefficientRepository, overlapCheckRepository, recomputeStatusRepository);
    }

    @Test
    void throwsPlantNotFound_andReadsNothing_whenThePlantIsGone() {
        when(lockPlantRepository.lockForCoefficientDates(PlantId.of(PLANT_ID))).thenReturn(false);

        assertThatThrownBy(() -> service().setDates(PLANT_ID, AGREEMENT_ID, REQUEST))
                .isInstanceOf(PlantNotFoundException.class);

        verifyNoInteractions(planner, saveCoefficientRepository, getCoefficientRepository);
    }
}
