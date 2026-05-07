package org.lucoenergia.conluz.infrastructure.consumption.shelly.persist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.config.ShellyConfigEntity;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetShellyConfigRepositoryDatabaseTest {

    @Mock
    private ShellyConfigRepository shellyConfigRepository;

    @InjectMocks
    private SetShellyConfigRepositoryDatabase setShellyConfigRepositoryDatabase;

    @Test
    void testSetShellyConfiguration_WhenNoExistingConfig_CreatesNewConfiguration() {
        // Arrange
        UUID newId = UUID.randomUUID();
        ShellyConfig inputConfig = new ShellyConfig.Builder()
                .setEnabled(true)
                .build();
        when(shellyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(shellyConfigRepository.save(any(ShellyConfigEntity.class))).thenAnswer(invocation -> {
            ShellyConfigEntity entity = invocation.getArgument(0);
            entity.setId(newId);
            return entity;
        });

        // Act
        ShellyConfig result = setShellyConfigRepositoryDatabase.setShellyConfiguration(inputConfig);

        // Assert
        assertNotNull(result);
        assertEquals(newId, result.getId());
        assertTrue(result.getEnabled());
        verify(shellyConfigRepository, times(1)).findFirstByOrderByIdAsc();
        verify(shellyConfigRepository, times(1)).save(any(ShellyConfigEntity.class));
    }

    @Test
    void testSetShellyConfiguration_WhenExistingConfig_UpdatesExistingConfiguration() {
        // Arrange
        UUID existingId = UUID.randomUUID();
        ShellyConfig inputConfig = new ShellyConfig.Builder()
                .setEnabled(false)
                .build();
        ShellyConfigEntity existingEntity = new ShellyConfigEntity();
        existingEntity.setId(existingId);
        existingEntity.setEnabled(true);
        when(shellyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existingEntity));
        when(shellyConfigRepository.save(any(ShellyConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ShellyConfig result = setShellyConfigRepositoryDatabase.setShellyConfiguration(inputConfig);

        // Assert
        assertNotNull(result);
        assertEquals(existingId, result.getId());
        assertFalse(result.getEnabled());
        verify(shellyConfigRepository, times(1)).findFirstByOrderByIdAsc();
        verify(shellyConfigRepository, times(1)).save(existingEntity);
    }
}