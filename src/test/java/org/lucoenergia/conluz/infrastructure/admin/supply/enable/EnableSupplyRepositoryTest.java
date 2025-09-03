package org.lucoenergia.conluz.infrastructure.admin.supply.enable;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.enable.EnableSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnableSupplyRepositoryTest {

    private final SupplyRepository repository = Mockito.mock(SupplyRepository.class);
    private final SupplyEntityMapper mapper = Mockito.mock(SupplyEntityMapper.class);

    private final EnableSupplyRepository enableSupplyRepositoryDatabase = new EnableSupplyRepositoryDatabase(repository, mapper);

    @Test
    void shouldEnableSupplyWhenDisabled() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        SupplyEntity supplyEntity = new SupplyEntity();
        supplyEntity.setId(supplyUuid);
        supplyEntity.setEnabled(false);
        when(repository.findById(supplyUuid)).thenReturn(Optional.of(supplyEntity));
        Supply mappedSupply = mock(Supply.class);
        when(repository.save(supplyEntity)).thenReturn(supplyEntity);
        when(mapper.map(supplyEntity)).thenReturn(mappedSupply);

        Supply result = enableSupplyRepositoryDatabase.enable(supplyId);

        assertNotNull(result);
        assertEquals(mappedSupply, result);
        assertTrue(supplyEntity.getEnabled());
        verify(repository).findById(supplyUuid);
        verify(repository).save(supplyEntity);
        verify(mapper).map(supplyEntity);
    }

    @Test
    void shouldReturnMappedSupplyWhenAlreadyEnabled() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        SupplyEntity supplyEntity = new SupplyEntity();
        supplyEntity.setId(supplyUuid);
        supplyEntity.setEnabled(true);
        when(repository.findById(supplyUuid)).thenReturn(Optional.of(supplyEntity));
        Supply mappedSupply = mock(Supply.class);
        when(mapper.map(supplyEntity)).thenReturn(mappedSupply);

        Supply result = enableSupplyRepositoryDatabase.enable(supplyId);

        assertNotNull(result);
        assertEquals(mappedSupply, result);
        assertTrue(supplyEntity.getEnabled());
        verify(repository).findById(supplyUuid);
        verify(mapper).map(supplyEntity);
        verify(repository, never()).save(supplyEntity);
    }

    @Test
    void shouldThrowSupplyNotFoundExceptionWhenSupplyDoesNotExist() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(repository.findById(supplyUuid)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> enableSupplyRepositoryDatabase.enable(supplyId));

        verify(repository).findById(supplyUuid);
        verifyNoInteractions(mapper);
    }
}