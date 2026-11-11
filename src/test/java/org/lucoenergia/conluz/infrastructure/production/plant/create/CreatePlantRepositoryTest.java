package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.production.plant.PlantCannotBeCreatedException;
import org.lucoenergia.conluz.domain.production.plant.PlantCilAlreadyInUseException;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.SupplyAlreadyHasPlantException;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write checks of PLT-002 and PLT-005 on their own, with the persistence mocked: the database constraints
 * behind them answer the same refusals, so only a test without the database can tell the checks are there.
 */
class CreatePlantRepositoryTest {

    private final SupplyRepository supplyRepository = mock(SupplyRepository.class);
    private final PlantRepository plantRepository = mock(PlantRepository.class);
    private final CreatePlantRepository repository = new CreatePlantRepositoryDatabase(supplyRepository,
            plantRepository, mock(SupplyEntityMapper.class), mock(PlantEntityMapper.class));

    @Test
    @DisplayName("PLT-002 creating a plant on a supply that already has a plant is refused before anything is saved")
    void creatingAPlantOnASupplyThatAlreadyHasAPlantIsRefusedBeforeAnythingIsSaved() {
        SupplyEntity supply = SupplyMother.randomEntity().build();
        SupplyId supplyId = SupplyId.of(supply.getId());
        when(supplyRepository.findById(supply.getId())).thenReturn(Optional.of(supply));
        when(plantRepository.countByProviderCode(anyString())).thenReturn(0);
        when(plantRepository.existsBySupplyId(supply.getId())).thenReturn(true);

        SupplyAlreadyHasPlantException exception = assertThrows(SupplyAlreadyHasPlantException.class,
                () -> repository.create(PlantMother.random().build(), supplyId));

        assertEquals(supplyId, exception.getSupplyId());
        verify(plantRepository, never()).save(any());
        verify(plantRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("PLT-005 creating a plant with a CIL another plant holds is refused before anything is saved")
    void creatingAPlantWithACilAnotherPlantHoldsIsRefusedBeforeAnythingIsSaved() {
        SupplyId supplyId = givenAFreeSupply();
        when(plantRepository.existsByCil("ES0031406912345678AB1F001")).thenReturn(true);

        assertThrows(PlantCilAlreadyInUseException.class,
                () -> repository.create(PlantMother.random().withCil("ES0031406912345678AB1F001").build(), supplyId));

        verify(plantRepository, never()).save(any());
        verify(plantRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("PLT-005 a plant without a CIL is never checked against the other plants' CILs")
    void aPlantWithoutACilIsNotChecked() {
        SupplyId supplyId = givenAFreeSupply();

        // The mocked persistence never finds the saved row, so the create ends there, after the checks
        assertThrows(PlantCannotBeCreatedException.class,
                () -> repository.create(PlantMother.random().withCil(null).build(), supplyId));

        verify(plantRepository, never()).existsByCil(any());
    }

    @Test
    @DisplayName("PLT-005 AC9 a concurrent writer rejected by plants_cil_uq is refused as PLANT_CIL_ALREADY_IN_USE")
    void aConcurrentWriterRejectedByTheCilConstraintIsRefusedAsACilConflict() {
        SupplyId supplyId = givenAFreeSupply();
        DataIntegrityViolationException violation = violationOf("plants_cil_uq");
        when(plantRepository.saveAndFlush(any())).thenThrow(violation);

        PlantCilAlreadyInUseException exception = assertThrows(PlantCilAlreadyInUseException.class,
                () -> repository.create(PlantMother.random().withCil("ES0031406912345678AB1F001").build(), supplyId));

        assertEquals(violation, exception.getCause());
    }

    @Test
    @DisplayName("PLT-002 a concurrent writer rejected by plants_supply_id_uq is still refused as SUPPLY_ALREADY_HAS_PLANT")
    void aConcurrentWriterRejectedByTheSupplyConstraintIsStillRefusedAsASupplyConflict() {
        SupplyId supplyId = givenAFreeSupply();
        when(plantRepository.saveAndFlush(any())).thenThrow(violationOf("plants_supply_id_uq"));

        SupplyAlreadyHasPlantException exception = assertThrows(SupplyAlreadyHasPlantException.class,
                () -> repository.create(PlantMother.random().withCil("ES0031406912345678AB1F001").build(), supplyId));

        assertEquals(supplyId, exception.getSupplyId());
    }

    @Test
    @DisplayName("PLT-005 an integrity violation of any other constraint is rethrown as it was")
    void anyOtherIntegrityViolationIsRethrownAsItWas() {
        SupplyId supplyId = givenAFreeSupply();
        DataIntegrityViolationException violation = violationOf("plants_id_pk");
        when(plantRepository.saveAndFlush(any())).thenThrow(violation);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> repository.create(PlantMother.random().withCil("ES0031406912345678AB1F001").build(), supplyId));

        assertEquals(violation, thrown);
    }

    private SupplyId givenAFreeSupply() {
        SupplyEntity supply = SupplyMother.randomEntity().build();
        when(supplyRepository.findById(supply.getId())).thenReturn(Optional.of(supply));
        when(plantRepository.countByProviderCode(anyString())).thenReturn(0);
        when(plantRepository.existsBySupplyId(supply.getId())).thenReturn(false);
        return SupplyId.of(supply.getId());
    }

    private static DataIntegrityViolationException violationOf(String constraintName) {
        ServerErrorMessage serverError = new ServerErrorMessage("SERROR\0C23505\0n" + constraintName + "\0");
        return new DataIntegrityViolationException("could not execute statement", new PSQLException(serverError));
    }
}
