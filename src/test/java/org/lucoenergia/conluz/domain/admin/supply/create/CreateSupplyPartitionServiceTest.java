package org.lucoenergia.conluz.domain.admin.supply.create;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.*;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyPartitionRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartitionId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyPartitionDto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateSupplyPartitionServiceTest {

    private CreateSupplyPartitionRepository repository;
    private GetSupplyPartitionRepository getSupplyPartitionRepository;
    private GetSupplyRepository getSupplyRepository;
    private GetSharingAgreementRepository getSharingAgreementRepository;
    private CreateSupplyPartitionService service;

    @BeforeEach
    void setUp() {
        repository = mock(CreateSupplyPartitionRepository.class);
        getSupplyPartitionRepository = mock(GetSupplyPartitionRepository.class);
        getSupplyRepository = mock(GetSupplyRepository.class);
        getSharingAgreementRepository = mock(GetSharingAgreementRepository.class);
        service = new CreateSupplyPartitionService(
                repository,
                getSupplyPartitionRepository,
                getSupplyRepository,
                getSharingAgreementRepository
        );
    }

    @Test
    void testValidateTotalCoefficientSuccess() {
        // arrange
        List<CreateSupplyPartitionDto> suppliesPartitions = new ArrayList<>();

        CreateSupplyPartitionDto dto1 = new CreateSupplyPartitionDto();
        dto1.setCode("SUPPLY1");
        dto1.setCoefficient(0.5);

        CreateSupplyPartitionDto dto2 = new CreateSupplyPartitionDto();
        dto2.setCode("SUPPLY2");
        dto2.setCoefficient(0.5);

        suppliesPartitions.add(dto1);
        suppliesPartitions.add(dto2);

        // act & assert
        assertDoesNotThrow(() -> service.validateTotalCoefficient(suppliesPartitions));
    }

    @Test
    void testValidateTotalCoefficientFailure() {
        // arrange
        List<CreateSupplyPartitionDto> suppliesPartitions = new ArrayList<>();

        CreateSupplyPartitionDto dto1 = new CreateSupplyPartitionDto();
        dto1.setCode("SUPPLY1");
        dto1.setCoefficient(0.4);

        CreateSupplyPartitionDto dto2 = new CreateSupplyPartitionDto();
        dto2.setCode("SUPPLY2");
        dto2.setCoefficient(0.5);

        suppliesPartitions.add(dto1);
        suppliesPartitions.add(dto2);

        // act & assert
        assertThrows(IllegalArgumentException.class, () -> service.validateTotalCoefficient(suppliesPartitions));
    }

    @Test
    void testCreateWhenSupplyDoesNotExist() {
        // arrange
        SupplyCode code = SupplyCode.of("SUPPLY1");
        Double coefficient = 0.500000;
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(UUID.randomUUID());

        when(getSupplyRepository.findByCode(code)).thenReturn(Optional.empty());

        // act & assert
        assertThrows(SupplyNotFoundException.class, () -> service.create(code, coefficient, sharingAgreementId));
        verify(getSupplyRepository).findByCode(code);
        verifyNoMoreInteractions(getSupplyRepository);
        verifyNoInteractions(getSharingAgreementRepository);
        verifyNoInteractions(getSupplyPartitionRepository);
        verifyNoInteractions(repository);
    }

    @Test
    void testCreateWhenSharingAgreementDoesNotExist() {
        // arrange
        SupplyCode code = SupplyCode.of("SUPPLY1");
        Double coefficient = 0.500000;
        UUID supplyId = UUID.randomUUID();
        Supply supply = new Supply.Builder()
                .withId(supplyId)
                .withCode("SUPPLY1")
                .build();
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(UUID.randomUUID());

        when(getSupplyRepository.findByCode(code)).thenReturn(Optional.of(supply));
        when(getSharingAgreementRepository.findById(sharingAgreementId)).thenReturn(Optional.empty());

        // act & assert
        assertThrows(SharingAgreementNotFoundException.class, () -> service.create(code, coefficient, sharingAgreementId));
        verify(getSupplyRepository).findByCode(code);
        verify(getSharingAgreementRepository).findById(sharingAgreementId);
        verifyNoMoreInteractions(getSupplyRepository, getSharingAgreementRepository);
        verifyNoInteractions(getSupplyPartitionRepository);
        verifyNoInteractions(repository);
    }

    @Test
    void testCreateWithInvalidCoefficientLessThanZero() {
        // arrange
        SupplyCode code = SupplyCode.of("SUPPLY1");
        Double coefficient = -0.1;
        UUID supplyId = UUID.randomUUID();
        Supply supply = new Supply.Builder()
                .withId(supplyId)
                .withCode("SUPPLY1")
                .build();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement(agreementId, LocalDate.now(), LocalDate.now().plusYears(1));
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(agreementId);

        when(getSupplyRepository.findByCode(code)).thenReturn(Optional.of(supply));
        when(getSharingAgreementRepository.findById(sharingAgreementId)).thenReturn(Optional.of(agreement));

        // act & assert
        assertThrows(IllegalArgumentException.class, () -> service.create(code, coefficient, sharingAgreementId));
        verify(getSupplyRepository).findByCode(code);
        verify(getSharingAgreementRepository).findById(sharingAgreementId);
        verifyNoMoreInteractions(getSupplyRepository, getSharingAgreementRepository);
        verifyNoInteractions(getSupplyPartitionRepository);
        verifyNoInteractions(repository);
    }

    @Test
    void testCreateWithInvalidCoefficientGreaterThanOne() {
        // arrange
        SupplyCode code = SupplyCode.of("SUPPLY1");
        Double coefficient = 1.1;
        UUID supplyId = UUID.randomUUID();
        Supply supply = new Supply.Builder()
                .withId(supplyId)
                .withCode("SUPPLY1")
                .build();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement(agreementId, LocalDate.now(), LocalDate.now().plusYears(1));
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(agreementId);

        when(getSupplyRepository.findByCode(code)).thenReturn(Optional.of(supply));
        when(getSharingAgreementRepository.findById(sharingAgreementId)).thenReturn(Optional.of(agreement));

        // act & assert
        assertThrows(IllegalArgumentException.class, () -> service.create(code, coefficient, sharingAgreementId));
        verify(getSupplyRepository).findByCode(code);
        verify(getSharingAgreementRepository).findById(sharingAgreementId);
        verifyNoMoreInteractions(getSupplyRepository, getSharingAgreementRepository);
        verifyNoInteractions(getSupplyPartitionRepository);
        verifyNoInteractions(repository);
    }

    @Test
    void testCreateWithInvalidCoefficientScale() {
        // arrange
        SupplyCode code = SupplyCode.of("SUPPLY1");
        Double coefficient = 0.5; // Not 6 decimal places
        UUID supplyId = UUID.randomUUID();
        Supply supply = new Supply.Builder()
                .withId(supplyId)
                .withCode("SUPPLY1")
                .build();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement(agreementId, LocalDate.now(), LocalDate.now().plusYears(1));
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(agreementId);

        when(getSupplyRepository.findByCode(code)).thenReturn(Optional.of(supply));
        when(getSharingAgreementRepository.findById(sharingAgreementId)).thenReturn(Optional.of(agreement));

        // act & assert
        assertThrows(IllegalArgumentException.class, () -> service.create(code, coefficient, sharingAgreementId));
        verify(getSupplyRepository).findByCode(code);
        verify(getSharingAgreementRepository).findById(sharingAgreementId);
        verifyNoMoreInteractions(getSupplyRepository, getSharingAgreementRepository);
        verifyNoInteractions(getSupplyPartitionRepository);
        verifyNoInteractions(repository);
    }

    @Test
    void testCreateWhenPartitionAlreadyExists() {
        // arrange
        SupplyCode code = SupplyCode.of("SUPPLY1");
        // Use a specific value that will have exactly 6 decimal places
        Double coefficient = 0.123456;
        UUID supplyId = UUID.randomUUID();
        Supply supply = new Supply.Builder()
                .withId(supplyId)
                .withCode("SUPPLY1")
                .build();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement(agreementId, LocalDate.now(), LocalDate.now().plusYears(1));
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(agreementId);
        UUID partitionId = UUID.randomUUID();
        SupplyPartition existingPartition = new SupplyPartition(partitionId, supply, agreement, 0.300000);
        SupplyPartition updatedPartition = new SupplyPartition(partitionId, supply, agreement, coefficient);

        when(getSupplyRepository.findByCode(code)).thenReturn(Optional.of(supply));
        when(getSharingAgreementRepository.findById(sharingAgreementId)).thenReturn(Optional.of(agreement));
        when(getSupplyPartitionRepository.findBySupplyAndSharingAgreement(any(SupplyId.class), any(SharingAgreementId.class)))
                .thenReturn(Optional.of(existingPartition));
        when(repository.updateCoefficient(any(SupplyPartitionId.class), eq(coefficient))).thenReturn(updatedPartition);

        // act
        SupplyPartition result = service.create(code, coefficient, sharingAgreementId);

        // assert
        assertNotNull(result);
        assertEquals(partitionId, result.getId());
        assertEquals(coefficient, result.getCoefficient());
        verify(getSupplyRepository).findByCode(code);
        verify(getSharingAgreementRepository).findById(sharingAgreementId);
        verify(getSupplyPartitionRepository).findBySupplyAndSharingAgreement(any(SupplyId.class), any(SharingAgreementId.class));
        verify(repository).updateCoefficient(any(SupplyPartitionId.class), eq(coefficient));
        verifyNoMoreInteractions(getSupplyRepository, getSharingAgreementRepository, getSupplyPartitionRepository, repository);
    }

    @Test
    void testCreateWhenPartitionDoesNotExist() {
        // arrange
        SupplyCode code = SupplyCode.of("SUPPLY1");
        // Use a specific value that will have exactly 6 decimal places
        Double coefficient = 0.123456;
        UUID supplyId = UUID.randomUUID();
        Supply supply = new Supply.Builder()
                .withId(supplyId)
                .withCode("SUPPLY1")
                .build();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement(agreementId, LocalDate.now(), LocalDate.now().plusYears(1));
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(agreementId);
        UUID partitionId = UUID.randomUUID();
        SupplyPartition newPartition = new SupplyPartition(partitionId, supply, agreement, coefficient);

        when(getSupplyRepository.findByCode(code)).thenReturn(Optional.of(supply));
        when(getSharingAgreementRepository.findById(sharingAgreementId)).thenReturn(Optional.of(agreement));
        when(getSupplyPartitionRepository.findBySupplyAndSharingAgreement(any(SupplyId.class), any(SharingAgreementId.class)))
                .thenReturn(Optional.empty());
        when(repository.create(code, coefficient, sharingAgreementId)).thenReturn(newPartition);

        // act
        SupplyPartition result = service.create(code, coefficient, sharingAgreementId);

        // assert
        assertNotNull(result);
        assertEquals(partitionId, result.getId());
        assertEquals(coefficient, result.getCoefficient());
        verify(getSupplyRepository).findByCode(code);
        verify(getSharingAgreementRepository).findById(sharingAgreementId);
        verify(getSupplyPartitionRepository).findBySupplyAndSharingAgreement(any(SupplyId.class), any(SharingAgreementId.class));
        verify(repository).create(code, coefficient, sharingAgreementId);
        verifyNoMoreInteractions(getSupplyRepository, getSharingAgreementRepository, getSupplyPartitionRepository, repository);
    }
}
