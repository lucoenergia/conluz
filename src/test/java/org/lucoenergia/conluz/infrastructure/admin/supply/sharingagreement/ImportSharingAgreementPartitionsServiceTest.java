package org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.create.ValidateSupplyPartitionsService;
import org.lucoenergia.conluz.infrastructure.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.create.CreateSupplyPartitionDto;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.io.ImportSharingAgreementPartitionsResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.io.ImportSharingAgreementPartitionsServiceImpl;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileParser;
import org.springframework.context.MessageSource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportSharingAgreementPartitionsServiceTest {

    private final SharingAgreementRepository sharingAgreementRepository = mock(SharingAgreementRepository.class);
    private final SupplyRepository supplyRepository = mock(SupplyRepository.class);
    private final SupplyPartitionRepository supplyPartitionRepository = mock(SupplyPartitionRepository.class);
    private final ValidateSupplyPartitionsService validateSupplyPartitionsService = mock(ValidateSupplyPartitionsService.class);
    private final CsvFileParser csvFileParser = mock(CsvFileParser.class);
    private final MessageSource messageSource = mock(MessageSource.class);
    private final ImportSharingAgreementPartitionsServiceImpl service = new ImportSharingAgreementPartitionsServiceImpl(
            sharingAgreementRepository, supplyRepository, supplyPartitionRepository,
            validateSupplyPartitionsService, csvFileParser, messageSource);

    @Test
    void testImportPartitions_whenSharingAgreementNotFound_shouldThrowSharingAgreementNotFoundException() {
        SharingAgreementId id = SharingAgreementId.of(UUID.randomUUID());
        MultipartFile file = mock(MultipartFile.class);

        when(sharingAgreementRepository.findById(id.getId())).thenReturn(Optional.empty());

        assertThrows(SharingAgreementNotFoundException.class, () -> service.importPartitions(id, file));
        verifyNoInteractions(csvFileParser);
    }

    @Test
    void testImportPartitions_whenFileCannotBeParsed_shouldThrowIllegalArgumentException() throws Exception {
        SharingAgreementId id = SharingAgreementId.of(UUID.randomUUID());
        MultipartFile file = mock(MultipartFile.class);

        when(sharingAgreementRepository.findById(id.getId()))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(id.getId())));
        when(file.getInputStream()).thenThrow(new IOException("disk error"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.importPartitions(id, file));

        assertTrue(exception.getMessage().startsWith("Unable to parse file:"));
    }

    @Test
    void testImportPartitions_whenCoefficientIsNull_shouldThrowInvalidSupplyPartitionCoefficientException()
            throws Exception {
        SharingAgreementId id = SharingAgreementId.of(UUID.randomUUID());
        MultipartFile file = mock(MultipartFile.class);

        CreateSupplyPartitionDto dto = new CreateSupplyPartitionDto();
        dto.setCode("ES001");
        dto.setCoefficient(null);

        when(sharingAgreementRepository.findById(id.getId()))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(id.getId())));
        when(file.getInputStream()).thenReturn(mock(InputStream.class));
        when(csvFileParser.parse(any(InputStream.class), eq(CreateSupplyPartitionDto.class), eq(';')))
                .thenReturn(List.of(dto));
        when(messageSource.getMessage(eq("error.supply.partitions.invalid.coefficient.our.of.range"), any(), any()))
                .thenReturn("Coefficient out of range");

        assertThrows(InvalidSupplyPartitionCoefficientException.class, () -> service.importPartitions(id, file));
    }

    @Test
    void testImportPartitions_whenCoefficientIsNegative_shouldThrowInvalidSupplyPartitionCoefficientException()
            throws Exception {
        SharingAgreementId id = SharingAgreementId.of(UUID.randomUUID());
        MultipartFile file = mock(MultipartFile.class);

        CreateSupplyPartitionDto dto = new CreateSupplyPartitionDto();
        dto.setCode("ES001");
        dto.setCoefficient(-0.1);

        when(sharingAgreementRepository.findById(id.getId()))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(id.getId())));
        when(file.getInputStream()).thenReturn(mock(InputStream.class));
        when(csvFileParser.parse(any(InputStream.class), eq(CreateSupplyPartitionDto.class), eq(';')))
                .thenReturn(List.of(dto));
        when(messageSource.getMessage(eq("error.supply.partitions.invalid.coefficient.our.of.range"), any(), any()))
                .thenReturn("Coefficient out of range");

        assertThrows(InvalidSupplyPartitionCoefficientException.class, () -> service.importPartitions(id, file));
    }

    @Test
    void testImportPartitions_whenCoefficientIsGreaterThanOne_shouldThrowInvalidSupplyPartitionCoefficientException()
            throws Exception {
        SharingAgreementId id = SharingAgreementId.of(UUID.randomUUID());
        MultipartFile file = mock(MultipartFile.class);

        CreateSupplyPartitionDto dto = new CreateSupplyPartitionDto();
        dto.setCode("ES001");
        dto.setCoefficient(1.5);

        when(sharingAgreementRepository.findById(id.getId()))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(id.getId())));
        when(file.getInputStream()).thenReturn(mock(InputStream.class));
        when(csvFileParser.parse(any(InputStream.class), eq(CreateSupplyPartitionDto.class), eq(';')))
                .thenReturn(List.of(dto));
        when(messageSource.getMessage(eq("error.supply.partitions.invalid.coefficient.our.of.range"), any(), any()))
                .thenReturn("Coefficient out of range");

        assertThrows(InvalidSupplyPartitionCoefficientException.class, () -> service.importPartitions(id, file));
    }

    @Test
    void testImportPartitions_whenTotalCoefficientIsInvalid_shouldThrowInvalidSupplyPartitionCoefficientException()
            throws Exception {
        SharingAgreementId id = SharingAgreementId.of(UUID.randomUUID());
        MultipartFile file = mock(MultipartFile.class);

        CreateSupplyPartitionDto dto1 = new CreateSupplyPartitionDto();
        dto1.setCode("ES001");
        dto1.setCoefficient(0.3);

        CreateSupplyPartitionDto dto2 = new CreateSupplyPartitionDto();
        dto2.setCode("ES002");
        dto2.setCoefficient(0.3);

        when(sharingAgreementRepository.findById(id.getId()))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(id.getId())));
        when(file.getInputStream()).thenReturn(mock(InputStream.class));
        when(csvFileParser.parse(any(InputStream.class), eq(CreateSupplyPartitionDto.class), eq(';')))
                .thenReturn(List.of(dto1, dto2));
        doThrow(new InvalidSupplyPartitionCoefficientException("Total must be 1"))
                .when(validateSupplyPartitionsService).validateTotalCoefficient(anyList());

        assertThrows(InvalidSupplyPartitionCoefficientException.class, () -> service.importPartitions(id, file));
    }

    @Test
    void testImportPartitions_whenSupplyNotFoundByCode_shouldAddErrorToResponse() throws Exception {
        SharingAgreementId id = SharingAgreementId.of(UUID.randomUUID());
        MultipartFile file = mock(MultipartFile.class);

        CreateSupplyPartitionDto dto = new CreateSupplyPartitionDto();
        dto.setCode("ES001");
        dto.setCoefficient(1.0);

        when(sharingAgreementRepository.findById(id.getId()))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(id.getId())));
        when(file.getInputStream()).thenReturn(mock(InputStream.class));
        when(csvFileParser.parse(any(InputStream.class), eq(CreateSupplyPartitionDto.class), eq(';')))
                .thenReturn(List.of(dto));
        when(supplyRepository.findByCode("ES001")).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("error.supply.not.found.by.code"), any(), any()))
                .thenReturn("Supply not found");

        ImportSharingAgreementPartitionsResponse response = service.importPartitions(id, file);

        assertTrue(response.getCreated().isEmpty());
        assertEquals(1, response.getErrors().size());
        assertEquals("ES001", response.getErrors().get(0).getItem());
        verifyNoInteractions(supplyPartitionRepository);
    }

    @Test
    void testImportPartitions_whenNewPartition_shouldCreatePartitionAndReturnSuccess() throws Exception {
        UUID agreementUuid = UUID.randomUUID();
        SharingAgreementId id = SharingAgreementId.of(agreementUuid);
        MultipartFile file = mock(MultipartFile.class);

        CreateSupplyPartitionDto dto1 = new CreateSupplyPartitionDto();
        dto1.setCode("ES001");
        dto1.setCoefficient(1.0);

        CreateSupplyPartitionDto dto2 = new CreateSupplyPartitionDto();
        dto2.setCode("ES002");
        dto2.setCoefficient(0.0);

        SupplyEntity supplyEntity1 = SupplyEntityMother.random();
        SupplyEntity supplyEntity2 = SupplyEntityMother.random();

        when(sharingAgreementRepository.findById(agreementUuid))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(agreementUuid)));
        when(file.getInputStream()).thenReturn(mock(InputStream.class));
        when(csvFileParser.parse(any(InputStream.class), eq(CreateSupplyPartitionDto.class), eq(';')))
                .thenReturn(List.of(dto1, dto2));
        when(supplyRepository.findByCode("ES001")).thenReturn(Optional.of(supplyEntity1));
        when(supplyRepository.findByCode("ES002")).thenReturn(Optional.of(supplyEntity2));
        when(supplyPartitionRepository.findBySupplyIdAndSharingAgreementId(supplyEntity1.getId(), agreementUuid))
                .thenReturn(Optional.empty());
        when(supplyPartitionRepository.findBySupplyIdAndSharingAgreementId(supplyEntity2.getId(), agreementUuid))
                .thenReturn(Optional.empty());

        ImportSharingAgreementPartitionsResponse response = service.importPartitions(id, file);

        assertEquals(2, response.getCreated().size());
        assertEquals("ES001", response.getCreated().get(0));
        assertEquals("ES002", response.getCreated().get(1));
        assertTrue(response.getErrors().isEmpty());
        verify(supplyPartitionRepository, times(2)).save(any(SupplyPartitionEntity.class));
        verify(supplyRepository, times(1)).save(supplyEntity1);
        verify(supplyRepository, times(1)).save(supplyEntity2);
    }

    @Test
    void testImportPartitions_whenExistingPartition_shouldUpdatePartitionAndReturnSuccess() throws Exception {
        UUID agreementUuid = UUID.randomUUID();
        SharingAgreementId id = SharingAgreementId.of(agreementUuid);
        MultipartFile file = mock(MultipartFile.class);

        CreateSupplyPartitionDto dto = new CreateSupplyPartitionDto();
        dto.setCode("ES001");
        dto.setCoefficient(0.75);

        SupplyEntity supplyEntity = SupplyEntityMother.random();
        SupplyPartitionEntity existingPartition = SupplyPartitionEntityMother.random(
                supplyEntity, SharingAgreementEntityMother.withId(agreementUuid));

        when(sharingAgreementRepository.findById(agreementUuid))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(agreementUuid)));
        when(file.getInputStream()).thenReturn(mock(InputStream.class));
        when(csvFileParser.parse(any(InputStream.class), eq(CreateSupplyPartitionDto.class), eq(';')))
                .thenReturn(List.of(dto));
        when(supplyRepository.findByCode("ES001")).thenReturn(Optional.of(supplyEntity));
        when(supplyPartitionRepository.findBySupplyIdAndSharingAgreementId(supplyEntity.getId(), agreementUuid))
                .thenReturn(Optional.of(existingPartition));

        ImportSharingAgreementPartitionsResponse response = service.importPartitions(id, file);

        assertEquals(1, response.getCreated().size());
        assertEquals("ES001", response.getCreated().get(0));
        assertTrue(response.getErrors().isEmpty());
        assertEquals(0.75, existingPartition.getCoefficient());
        verify(supplyPartitionRepository, times(1)).save(existingPartition);
        verify(supplyRepository, times(1)).save(supplyEntity);
    }

    @Test
    void testImportPartitions_whenSaveThrowsException_shouldAddErrorToResponse() throws Exception {
        UUID agreementUuid = UUID.randomUUID();
        SharingAgreementId id = SharingAgreementId.of(agreementUuid);
        MultipartFile file = mock(MultipartFile.class);

        CreateSupplyPartitionDto dto = new CreateSupplyPartitionDto();
        dto.setCode("ES001");
        dto.setCoefficient(1.0);

        SupplyEntity supplyEntity = SupplyEntityMother.random();

        when(sharingAgreementRepository.findById(agreementUuid))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(agreementUuid)));
        when(file.getInputStream()).thenReturn(mock(InputStream.class));
        when(csvFileParser.parse(any(InputStream.class), eq(CreateSupplyPartitionDto.class), eq(';')))
                .thenReturn(List.of(dto));
        when(supplyRepository.findByCode("ES001")).thenReturn(Optional.of(supplyEntity));
        when(supplyPartitionRepository.findBySupplyIdAndSharingAgreementId(supplyEntity.getId(), agreementUuid))
                .thenReturn(Optional.empty());
        when(supplyPartitionRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        ImportSharingAgreementPartitionsResponse response = service.importPartitions(id, file);

        assertTrue(response.getCreated().isEmpty());
        assertEquals(1, response.getErrors().size());
        assertEquals("ES001", response.getErrors().get(0).getItem());
        assertEquals("DB error", response.getErrors().get(0).getErrorMessage());
    }

    @Test
    void testImportPartitions_withMixedRows_shouldReturnPartialSuccessAndErrors() throws Exception {
        UUID agreementUuid = UUID.randomUUID();
        SharingAgreementId id = SharingAgreementId.of(agreementUuid);
        MultipartFile file = mock(MultipartFile.class);

        CreateSupplyPartitionDto dto1 = new CreateSupplyPartitionDto();
        dto1.setCode("ES001");
        dto1.setCoefficient(0.6);

        CreateSupplyPartitionDto dto2 = new CreateSupplyPartitionDto();
        dto2.setCode("ES002");
        dto2.setCoefficient(0.4);

        SupplyEntity supplyEntity = SupplyEntityMother.random();

        when(sharingAgreementRepository.findById(agreementUuid))
                .thenReturn(Optional.of(SharingAgreementEntityMother.withId(agreementUuid)));
        when(file.getInputStream()).thenReturn(mock(InputStream.class));
        when(csvFileParser.parse(any(InputStream.class), eq(CreateSupplyPartitionDto.class), eq(';')))
                .thenReturn(List.of(dto1, dto2));
        when(supplyRepository.findByCode("ES001")).thenReturn(Optional.of(supplyEntity));
        when(supplyPartitionRepository.findBySupplyIdAndSharingAgreementId(supplyEntity.getId(), agreementUuid))
                .thenReturn(Optional.empty());
        when(supplyRepository.findByCode("ES002")).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("error.supply.not.found.by.code"), any(), any()))
                .thenReturn("Supply not found");

        ImportSharingAgreementPartitionsResponse response = service.importPartitions(id, file);

        assertEquals(1, response.getCreated().size());
        assertEquals("ES001", response.getCreated().get(0));
        assertEquals(1, response.getErrors().size());
        assertEquals("ES002", response.getErrors().get(0).getItem());
    }
}
