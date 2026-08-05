package org.lucoenergia.conluz.infrastructure.production.sharingagreement.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.create.CreateSharingAgreementServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateSharingAgreementServiceImplTest {

    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private CreateSharingAgreementRepository repository;

    private CreateSharingAgreementServiceImpl service() {
        return new CreateSharingAgreementServiceImpl(getPlantRepository, repository);
    }

    @Test
    void create_throwsPlantNotFound_whenPlantDoesNotExist() {
        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());
        CreateSharingAgreement request = new CreateSharingAgreement.Builder()
                .withName("name")
                .withNotes("notes")
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedBy(UUID.randomUUID())
                .build();

        assertThrows(PlantNotFoundException.class,
                () -> service().create(plantId, request));
    }

    @Test
    void create_buildsDraftAgreementUsingSuppliedPower() {
        UUID plantId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        Plant plant = PlantMother.random().withId(plantId).withTotalPower(7.25).build();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.of(plant));
        when(repository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CreateSharingAgreement request = new CreateSharingAgreement.Builder()
                .withName("2024 winter distribution")
                .withNotes("notes")
                .withInstalledPowerKw(BigDecimal.valueOf(3.5))
                .withCreatedBy(createdBy)
                .build();

        SharingAgreement result = service().create(plantId, request);

        assertEquals(plantId, result.getPlantId());
        assertEquals("2024 winter distribution", result.getName());
        assertEquals("notes", result.getNotes());
        assertEquals(SharingAgreementStatus.DRAFT, result.getStatus());
        assertEquals(0, BigDecimal.valueOf(3.5).compareTo(result.getInstalledPowerKw()));
        assertEquals(createdBy, result.getCreatedBy());
        assertNotNull(result.getId());
        assertNotNull(result.getCreatedAt());
    }
}
