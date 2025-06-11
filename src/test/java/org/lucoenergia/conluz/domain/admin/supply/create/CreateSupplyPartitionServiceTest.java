package org.lucoenergia.conluz.domain.admin.supply.create;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.*;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyPartitionRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyPartitionDto;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyPartitionServiceImpl;
import org.lucoenergia.conluz.domain.shared.response.CreationInBulkResponse;
import org.springframework.context.MessageSource;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateSupplyPartitionServiceTest {

    private CreateSupplyPartitionService service;
    private CreateSupplyPartitionRepository repository;
    private GetSupplyPartitionRepository getSupplyPartitionRepository;
    private GetSupplyRepository getSupplyRepository;
    private GetSharingAgreementRepository getSharingAgreementRepository;
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        repository = mock(CreateSupplyPartitionRepository.class);
        getSupplyPartitionRepository = mock(GetSupplyPartitionRepository.class);
        getSupplyRepository = mock(GetSupplyRepository.class);
        getSharingAgreementRepository = mock(GetSharingAgreementRepository.class);
        messageSource = mock(MessageSource.class);
        service = new CreateSupplyPartitionServiceImpl(
                repository,
                getSupplyPartitionRepository,
                getSupplyRepository,
                getSharingAgreementRepository,
                messageSource
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

        // act and assert
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

        // act and assert
        assertThrows(InvalidSupplyPartitionCoefficientException.class, () -> service.validateTotalCoefficient(suppliesPartitions));
    }

    @Test
    void testCreateWhenSupplyDoesNotExist() {
        // arrange
        SupplyCode code = SupplyCode.of("SUPPLY1");
        Double coefficient = 0.500000;
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(UUID.randomUUID());

        when(getSupplyRepository.findByCode(code)).thenReturn(Optional.empty());

        // act and assert
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

        // act and assert
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

        // act and assert
        assertThrows(InvalidSupplyPartitionCoefficientException.class, () -> service.create(code, coefficient, sharingAgreementId));
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

        // act and assert
        assertThrows(InvalidSupplyPartitionCoefficientException.class, () -> service.create(code, coefficient, sharingAgreementId));
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

        // act and assert
        assertThrows(InvalidSupplyPartitionCoefficientException.class, () -> service.create(code, coefficient, sharingAgreementId));
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

    @Test
    void testCreateInBulkWithValidSupplyPartitionsResultsInSuccessfulCreation() {
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(UUID.randomUUID());

        Supply supplyOne = mock(Supply.class);
        when(supplyOne.getCode()).thenReturn("Supply1");
        SupplyPartition newSupplyOnePartition = mock(SupplyPartition.class);

        Supply supplyTwo = mock(Supply.class);
        when(supplyTwo.getCode()).thenReturn("Supply2");
        SupplyPartition newSupplyTwoPartition = mock(SupplyPartition.class);

        CreateSupplyPartitionDto dto1 = new CreateSupplyPartitionDto();
        dto1.setCode(supplyOne.getCode());
        dto1.setCoefficient(0.612345);

        CreateSupplyPartitionDto dto2 = new CreateSupplyPartitionDto();
        dto2.setCode(supplyTwo.getCode());
        dto2.setCoefficient(0.387655);

        when(getSupplyRepository.findByCode(any(SupplyCode.class)))
                .thenAnswer(invocation -> {
                    SupplyCode code = invocation.getArgument(0);
                    if (code.getCode().equals(supplyOne.getCode())) {
                        return Optional.of(supplyOne);
                    } else if (code.getCode().equals(supplyTwo.getCode())) {
                        return Optional.of(supplyTwo);
                    }
                    return Optional.empty();
                });

        when(getSharingAgreementRepository.findById(argThat(id -> id.getId().equals(sharingAgreementId.getId()))))
                .thenReturn(Optional.of(mock(SharingAgreement.class)));
        when(repository.create(any(SupplyCode.class), eq(0.612345), any(SharingAgreementId.class))).thenReturn(newSupplyOnePartition);
        when(repository.create(any(SupplyCode.class), eq(0.387655), any(SharingAgreementId.class))).thenReturn(newSupplyTwoPartition);

        CreationInBulkResponse<CreateSupplyPartitionDto, SupplyPartition> response =
                service.createInBulk(List.of(dto1, dto2), sharingAgreementId);

        assertNotNull(response);
        assertEquals(2, response.getCreated().size());
        assertTrue(response.getCreated().contains(newSupplyOnePartition));
        assertTrue(response.getCreated().contains(newSupplyTwoPartition));
        assertTrue(response.getErrors().isEmpty());
        verify(repository, times(2)).create(any(SupplyCode.class), anyDouble(), eq(sharingAgreementId));
    }

    @Test
    void testCreateInBulkWithSumOfCoefficientsNotEqualToOneThrowsException() {
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(UUID.randomUUID());
        CreateSupplyPartitionDto dto1 = new CreateSupplyPartitionDto();
        dto1.setCode("Supply1");
        dto1.setCoefficient(0.612345);

        CreateSupplyPartitionDto dto2 = new CreateSupplyPartitionDto();
        dto2.setCode("Supply2");
        dto2.setCoefficient(0.612345);

        when(messageSource.getMessage(anyString(), any(), eq(Locale.getDefault()))).thenReturn("Error message");

        InvalidSupplyPartitionCoefficientException exception = assertThrows(
                InvalidSupplyPartitionCoefficientException.class,
                () -> service.createInBulk(List.of(dto1, dto2), sharingAgreementId)
        );

        assertEquals("Error message", exception.getMessage());
    }

    @Test
    void testCreateInBulkWithSupplyNotFoundAddsErrorToResponse() {
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(UUID.randomUUID());
        CreateSupplyPartitionDto dto = new CreateSupplyPartitionDto();
        dto.setCode("Supply1");
        dto.setCoefficient(1.000000);

        when(getSharingAgreementRepository.findById(argThat(id -> id.getId().equals(sharingAgreementId.getId()))))
                .thenReturn(Optional.of(mock(SharingAgreement.class)));
        when(getSupplyRepository.findByCode(SupplyCode.of("Supply1"))).thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), eq(Locale.getDefault()))).thenReturn("Supply not found");

        CreationInBulkResponse<CreateSupplyPartitionDto, SupplyPartition> response =
                service.createInBulk(List.of(dto), sharingAgreementId);

        assertEquals(0, response.getCreated().size());
        assertEquals(1, response.getErrors().size());
        assertEquals("Supply not found", response.getErrors().get(0).getErrorMessage());
    }

    @Test
    void testCreateInBulkWithSharingAgreementNotFoundThrowsException() {
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(UUID.randomUUID());
        CreateSupplyPartitionDto dto = new CreateSupplyPartitionDto();
        dto.setCode("Supply1");
        dto.setCoefficient(1.000000);

        when(getSupplyRepository.findByCode(SupplyCode.of("Supply1"))).thenReturn(Optional.of(mock(Supply.class)));
        when(getSharingAgreementRepository.findById(sharingAgreementId)).thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), eq(Locale.getDefault()))).thenReturn("Sharing agreement not found");

        SharingAgreementNotFoundException exception = assertThrows(
                SharingAgreementNotFoundException.class,
                () -> service.createInBulk(List.of(dto), sharingAgreementId)
        );

        assertEquals(sharingAgreementId, exception.getId());
    }

    @Test
    void testCreateInBulkWithDatabaseExceptionAddsErrorToResponse() {
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(UUID.randomUUID());
        CreateSupplyPartitionDto dto = new CreateSupplyPartitionDto();
        dto.setCode("Supply1");
        dto.setCoefficient(1.000000);

        when(getSupplyRepository.findByCode(SupplyCode.of("Supply1"))).thenReturn(Optional.of(mock(Supply.class)));
        when(getSharingAgreementRepository.findById(sharingAgreementId)).thenReturn(Optional.of(mock(SharingAgreement.class)));
        when(repository.create(SupplyCode.of("Supply1"), 0.5, sharingAgreementId)).thenThrow(new RuntimeException("Database error"));
        when(messageSource.getMessage(anyString(), any(), eq(Locale.getDefault()))).thenReturn("Unable to create supply partition");

        CreationInBulkResponse<CreateSupplyPartitionDto, SupplyPartition> response =
                service.createInBulk(List.of(dto), sharingAgreementId);

        assertEquals(0, response.getCreated().size());
        assertEquals(1, response.getErrors().size());
        assertEquals("Unable to create supply partition", response.getErrors().get(0).getErrorMessage());
    }

    @Test
    void testCreateInBulkWithEmptySupplyPartitionsListResultsInSuccessfulResponse() {
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(UUID.randomUUID());

        CreationInBulkResponse<CreateSupplyPartitionDto, SupplyPartition> response =
                service.createInBulk(List.of(), sharingAgreementId);

        assertNotNull(response);
        assertTrue(response.getErrors().isEmpty());
        assertTrue(response.getCreated().isEmpty());
    }
}
