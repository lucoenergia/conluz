package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SetHuaweiConfigurationRepositoryDatabaseTest {

    private final HuaweiConfigRepository huaweiConfigRepository = Mockito.mock(HuaweiConfigRepository.class);
    private final SetHuaweiConfigurationRepositoryDatabase repository = new SetHuaweiConfigurationRepositoryDatabase(huaweiConfigRepository);

    @Test
    void testSetHuaweiConfiguration_notExist() {
        // Given
        when(huaweiConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        HuaweiConfig testConfig = new HuaweiConfig.Builder()
                .setUsername("username1")
                .setPassword("password1")
                .setBaseUrl("http://mock-huawei.local/thirdData")
                .setEnabled(Boolean.TRUE)
                .build();

        // When
        HuaweiConfig result = repository.setHuaweiConfiguration(testConfig);

        // Then
        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
        assertEquals(testConfig.getBaseUrl(), result.getBaseUrl());
        assertEquals(testConfig.getEnabled(), result.getEnabled());
    }

    @Test
    void testSetHuaweiConfiguration_exist() {
        // Given
        HuaweiConfigEntity existConfigEntity = new HuaweiConfigEntity();
        existConfigEntity.setId(UUID.randomUUID());
        existConfigEntity.setUsername("username2");
        existConfigEntity.setPassword("password2");
        existConfigEntity.setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL);
        existConfigEntity.setEnabled(Boolean.TRUE);

        when(huaweiConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existConfigEntity));
        when(huaweiConfigRepository.save(any(HuaweiConfigEntity.class))).thenAnswer(invocation -> {
            HuaweiConfigEntity entity = (HuaweiConfigEntity) invocation.getArguments()[0];
            existConfigEntity.setUsername(entity.getUsername());
            existConfigEntity.setPassword(entity.getPassword());
            existConfigEntity.setBaseUrl(entity.getBaseUrl());
            existConfigEntity.setEnabled(entity.getEnabled());
            return existConfigEntity;
        });

        HuaweiConfig testConfig = new HuaweiConfig.Builder()
                .setUsername("newUsername")
                .setPassword("newPassword")
                .setBaseUrl("http://mock-huawei.local/thirdData")
                .setEnabled(Boolean.FALSE)
                .build();

        // When
        HuaweiConfig result = repository.setHuaweiConfiguration(testConfig);

        // Then
        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
        assertEquals(testConfig.getBaseUrl(), result.getBaseUrl());
        assertEquals(testConfig.getEnabled(), result.getEnabled());
    }

    @Test
    void testSetHuaweiConfiguration_defaultBaseUrlAppliedWhenNotSet() {
        // Given
        when(huaweiConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        HuaweiConfig testConfig = new HuaweiConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setEnabled(Boolean.TRUE)
                .build(); // baseUrl not set → should default

        // When
        HuaweiConfig result = repository.setHuaweiConfiguration(testConfig);

        // Then
        assertEquals(HuaweiConfig.DEFAULT_BASE_URL, result.getBaseUrl());
    }
}
