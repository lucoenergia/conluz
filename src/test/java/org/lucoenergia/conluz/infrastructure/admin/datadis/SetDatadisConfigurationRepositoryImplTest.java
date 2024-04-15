package org.lucoenergia.conluz.infrastructure.admin.datadis;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.datadis.DatadisConfig;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

class SetDatadisConfigurationRepositoryImplTest {

    private final DatadisConfigRepository datadisConfigRepository = Mockito.mock(DatadisConfigRepository.class);
    private final SetDatadisConfigurationRepositoryImpl repository = new SetDatadisConfigurationRepositoryImpl(datadisConfigRepository);

    @Test
    void testSetDatadisConfiguration_notExist() {
        // Given
        when(datadisConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        DatadisConfig testConfig = new DatadisConfig("username1", "password1");

        // When
        DatadisConfig result = repository.setDatadisConfiguration(testConfig);

        // Then
        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
    }

    @Test
    void testSetDatadisConfiguration_exist() {
        // Given
        DatadisConfigEntity existConfigEntity = new DatadisConfigEntity();
        existConfigEntity.setId(UUID.randomUUID());
        existConfigEntity.setUsername("username2");
        existConfigEntity.setPassword("password2");

        when(datadisConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existConfigEntity));
        when(datadisConfigRepository.save(any(DatadisConfigEntity.class))).thenAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            if (arguments != null && arguments.length > 0 && arguments[0] != null){
                DatadisConfigEntity datadisConfigEntity = (DatadisConfigEntity) arguments[0];
                existConfigEntity.setUsername(datadisConfigEntity.getUsername());
                existConfigEntity.setPassword(datadisConfigEntity.getPassword());
            }
            return existConfigEntity;
        });

        DatadisConfig testConfig = new DatadisConfig("newUsername", "newPassword");

        // When
        DatadisConfig result = repository.setDatadisConfiguration(testConfig);

        // Then
        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
    }
}