package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateSupplyRepositoryImplTest {

    SupplyRepository repository = Mockito.mock(SupplyRepository.class);
    SupplyEntityMapper mapper = Mockito.mock(SupplyEntityMapper.class);

    UpdateSupplyRepositoryImpl repositoryImpl = new UpdateSupplyRepositoryImpl(repository, mapper);

    @Test
    void testUpdateWithExistingSupply() {
        // Assemble
        UUID testSupplyUuid = UUID.randomUUID();
        Supply mockSupply = new Supply.Builder()
                .withId(testSupplyUuid)
                .withCode("code")
                .withName("name")
                .withAddress("address")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .withValidDateFrom(LocalDate.now())
                .withDistributor("distributor")
                .withDistributorCode("distributorCode")
                .withPointType(1)
                .build();
        SupplyEntity supplyEntity = new SupplyEntity();
        Mockito.when(repository.findById(testSupplyUuid)).thenReturn(Optional.of(supplyEntity));
        Mockito.when(repository.save(supplyEntity)).thenReturn(supplyEntity);
        Mockito.when(mapper.map(supplyEntity)).thenReturn(mockSupply);
        
        // Act
        Supply updatedSupply = repositoryImpl.update(mockSupply);
        
        // Assert
        assertEquals(updatedSupply, mockSupply);
        Mockito.verify(repository).save(supplyEntity);
    }

    @Test
    void testUpdateWithNonExistingSupply() {
        // Assemble
        UUID testSupplyUuid = UUID.randomUUID();
        Supply mockSupply = new Supply.Builder()
                .withId(testSupplyUuid)
                .withCode("code")
                .build();

        Mockito.when(repository.findById(testSupplyUuid)).thenReturn(Optional.empty());
        
        // Act and verify
        assertThrows(SupplyNotFoundException.class, () -> repositoryImpl.update(mockSupply));
    }
}