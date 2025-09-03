package org.lucoenergia.conluz.infrastructure.admin.supply.disable;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.disable.DisableSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisableSupplyRepositoryDatabaseTest {

    private final SupplyRepository supplyRepository = Mockito.mock(SupplyRepository.class);
    private final SupplyEntityMapper supplyEntityMapper = Mockito.mock(SupplyEntityMapper.class);

    private final DisableSupplyRepository repositoryDatabase = new DisableSupplyRepositoryDatabase(supplyRepository,
            supplyEntityMapper);

    @Test
    void shouldDisableSupplyWhenItIsEnabled() {
        UUID uuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(uuid);
        SupplyEntity supplyEntity = new SupplyEntity();
        supplyEntity.setId(uuid);
        supplyEntity.setEnabled(true);

        when(supplyRepository.findById(uuid)).thenReturn(Optional.of(supplyEntity));
        SupplyEntity disabledEntity = new SupplyEntity();
        disabledEntity.setId(uuid);
        disabledEntity.setEnabled(false);
        when(supplyRepository.save(supplyEntity)).thenReturn(disabledEntity);
        Supply mockSupply = mock(Supply.class);
        when(supplyEntityMapper.map(disabledEntity)).thenReturn(mockSupply);

        Supply result = repositoryDatabase.disable(supplyId);

        assertNotNull(result);
        assertEquals(mockSupply, result);
        assertFalse(supplyEntity.getEnabled());
        verify(supplyRepository, times(1)).findById(uuid);
        verify(supplyRepository, times(1)).save(supplyEntity);
        verify(supplyEntityMapper, times(1)).map(disabledEntity);
    }

    @Test
    void shouldNotDisableSupplyWhenAlreadyDisabled() {
        UUID uuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(uuid);
        SupplyEntity supplyEntity = new SupplyEntity();
        supplyEntity.setId(uuid);
        supplyEntity.setEnabled(false);

        when(supplyRepository.findById(uuid)).thenReturn(Optional.of(supplyEntity));
        Supply mockSupply = mock(Supply.class);
        when(supplyEntityMapper.map(supplyEntity)).thenReturn(mockSupply);

        Supply result = repositoryDatabase.disable(supplyId);

        assertNotNull(result);
        assertEquals(mockSupply, result);
        assertFalse(supplyEntity.getEnabled());
        verify(supplyRepository, times(1)).findById(uuid);
        verify(supplyRepository, never()).save(any(SupplyEntity.class));
        verify(supplyEntityMapper, times(1)).map(supplyEntity);
    }

    @Test
    void shouldThrowExceptionWhenSupplyNotFound() {
        UUID uuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(uuid);

        when(supplyRepository.findById(uuid)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> repositoryDatabase.disable(supplyId));

        verify(supplyRepository, times(1)).findById(uuid);
        verifyNoMoreInteractions(supplyRepository);
        verifyNoInteractions(supplyEntityMapper);
    }
}