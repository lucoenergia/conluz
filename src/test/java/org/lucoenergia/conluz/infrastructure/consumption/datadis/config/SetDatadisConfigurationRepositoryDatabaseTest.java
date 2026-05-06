package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

class SetDatadisConfigurationRepositoryDatabaseTest {

    private final DatadisConfigRepository datadisConfigRepository = Mockito.mock(DatadisConfigRepository.class);
    private final SetDatadisConfigurationRepositoryDatabase repository = new SetDatadisConfigurationRepositoryDatabase(datadisConfigRepository);

    @Test
    void testSetDatadisConfiguration_notExist() {
        // Given
        when(datadisConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(datadisConfigRepository.save(any(DatadisConfigEntity.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        DatadisConfig testConfig = new DatadisConfig.Builder()
                .setUsername("username1")
                .setPassword("password1")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build();

        // When
        DatadisConfig result = repository.setDatadisConfiguration(testConfig);

        // Then
        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
        assertEquals(testConfig.getBaseUrl(), result.getBaseUrl());
        assertEquals(testConfig.getEnabled(), result.getEnabled());
    }

    @Test
    void testSetDatadisConfiguration_exist() {
        // Given
        DatadisConfigEntity existConfigEntity = new DatadisConfigEntity();
        existConfigEntity.setId(UUID.randomUUID());
        existConfigEntity.setUsername("username2");
        existConfigEntity.setPassword("password2");
        existConfigEntity.setBaseUrl(DatadisConfig.DEFAULT_BASE_URL);
        existConfigEntity.setEnabled(Boolean.FALSE);

        when(datadisConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existConfigEntity));
        when(datadisConfigRepository.save(any(DatadisConfigEntity.class))).thenAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            if (arguments != null && arguments.length > 0 && arguments[0] != null) {
                DatadisConfigEntity datadisConfigEntity = (DatadisConfigEntity) arguments[0];
                existConfigEntity.setUsername(datadisConfigEntity.getUsername());
                existConfigEntity.setPassword(datadisConfigEntity.getPassword());
                existConfigEntity.setBaseUrl(datadisConfigEntity.getBaseUrl());
                existConfigEntity.setEnabled(datadisConfigEntity.getEnabled());
            }
            return existConfigEntity;
        });

        DatadisConfig testConfig = new DatadisConfig.Builder()
                .setUsername("newUsername")
                .setPassword("newPassword")
                .setBaseUrl("http://localhost:8080")
                .setEnabled(Boolean.TRUE)
                .build();

        // When
        DatadisConfig result = repository.setDatadisConfiguration(testConfig);

        // Then
        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
        assertEquals(testConfig.getBaseUrl(), result.getBaseUrl());
        assertEquals(testConfig.getEnabled(), result.getEnabled());
    }
}
