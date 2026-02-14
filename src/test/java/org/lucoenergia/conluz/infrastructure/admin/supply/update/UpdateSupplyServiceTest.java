package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyDto;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class UpdateSupplyServiceTest {

    private final GetSupplyRepository getSupplyRepository = Mockito.mock(GetSupplyRepository.class);
    private final UpdateSupplyRepository updateSupplyRepository = Mockito.mock(UpdateSupplyRepository.class);

    private final UpdateSupplyService updateSupplyService = new UpdateSupplyServiceImpl(getSupplyRepository,
            updateSupplyRepository);

    @Test
    void testUpdate_Success() {
        // Arrange
        UUID supplyIdValue = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyIdValue);
        Supply existingSupply = new Supply.Builder()
                .withId(supplyIdValue)
                .withCode("CODE123")
                .withName("Existing Supply")
                .withAddress("123 Street")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();

        UpdateSupplyDto updateSupplyDto = new UpdateSupplyDto.Builder()
                .code("NEWCODE123")
                .name("Updated Supply")
                .address("456 Avenue")
                .partitionCoefficient(2.0f)
                .build();

        Supply updatedSupply = new Supply.Builder()
                .withId(supplyIdValue)
                .withCode("NEWCODE123")
                .withName("Updated Supply")
                .withAddress("456 Avenue")
                .withPartitionCoefficient(2.0f)
                .withEnabled(true)
                .build();

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(existingSupply));
        when(updateSupplyRepository.update(any(Supply.class))).thenReturn(updatedSupply);

        // Act
        Supply result = updateSupplyService.update(supplyId, updateSupplyDto);

        // Assert
        Assertions.assertNotNull(result);
        Assertions.assertEquals("NEWCODE123", result.getCode());
        Assertions.assertEquals("Updated Supply", result.getName());
        Assertions.assertEquals("456 Avenue", result.getAddress());
        Assertions.assertEquals(2.0f, result.getPartitionCoefficient());
        verify(getSupplyRepository, times(1)).findById(supplyId);
        verify(updateSupplyRepository, times(1)).update(any(Supply.class));
    }

    @Test
    void testUpdate_SupplyNotFound() {
        // Arrange
        UUID supplyIdValue = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyIdValue);
        UpdateSupplyDto updateSupplyDto = new UpdateSupplyDto.Builder()
                .code("NEWCODE123")
                .name("Updated Supply")
                .address("456 Avenue")
                .partitionCoefficient(2.0f)
                .build();

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        // Act & Assert
        Assertions.assertThrows(SupplyNotFoundException.class, () -> {
            updateSupplyService.update(supplyId, updateSupplyDto);
        });

        verify(getSupplyRepository, times(1)).findById(supplyId);
        verifyNoInteractions(updateSupplyRepository);
    }

    @Test
    void testUpdate_ValidatesMappingToSupply() {
        // Arrange
        UUID supplyIdValue = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyIdValue);
        Supply existingSupply = new Supply.Builder()
                .withId(supplyIdValue)
                .withCode("CODE123")
                .withName("Existing Supply")
                .withAddress("123 Street")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();

        UpdateSupplyDto updateSupplyDto = mock(UpdateSupplyDto.class);
        Supply mappedSupply = new Supply.Builder()
                .withId(supplyIdValue)
                .withCode("MAPPEDCODE123")
                .withName("Mapped Supply")
                .withAddress("Mapped Address")
                .withPartitionCoefficient(3.0f)
                .withEnabled(true)
                .build();

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(existingSupply));
        when(updateSupplyDto.mapToSupply(any(Supply.Builder.class))).thenReturn(mappedSupply);
        when(updateSupplyRepository.update(mappedSupply)).thenReturn(mappedSupply);

        // Act
        Supply result = updateSupplyService.update(supplyId, updateSupplyDto);

        // Assert
        Assertions.assertNotNull(result);
        Assertions.assertEquals("MAPPEDCODE123", result.getCode());
        Assertions.assertEquals("Mapped Supply", result.getName());
        Assertions.assertEquals("Mapped Address", result.getAddress());
        Assertions.assertEquals(3.0f, result.getPartitionCoefficient());
        verify(updateSupplyDto, times(1)).mapToSupply(any(Supply.Builder.class));
        verify(updateSupplyRepository, times(1)).update(mappedSupply);
    }
}