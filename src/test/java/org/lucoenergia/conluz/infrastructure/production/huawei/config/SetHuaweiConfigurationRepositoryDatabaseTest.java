package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SetHuaweiConfigurationRepositoryDatabaseTest {

    private final HuaweiConfigRepository huaweiConfigRepository = Mockito.mock(HuaweiConfigRepository.class);
    private final SetHuaweiConfigurationRepositoryDatabase repository = new SetHuaweiConfigurationRepositoryDatabase(huaweiConfigRepository);

    @Test
    void testSetHuaweiConfiguration_notExist() {
        // Given
        when(huaweiConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        HuaweiConfig testConfig = new HuaweiConfig.Builder().setUsername("username1").setPassword("password1").build();

        // When
        HuaweiConfig result = repository.setHuaweiConfiguration(testConfig);

        // Then
        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
    }

    @Test
    void testSetHuaweiConfiguration_exist() {
        // Given
        HuaweiConfigEntity existConfigEntity = new HuaweiConfigEntity();
        existConfigEntity.setId(UUID.randomUUID());
        existConfigEntity.setUsername("username2");
        existConfigEntity.setPassword("password2");

        when(huaweiConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existConfigEntity));
        when(huaweiConfigRepository.save(any(HuaweiConfigEntity.class))).thenAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            if (arguments != null && arguments.length > 0 && arguments[0] != null){
                HuaweiConfigEntity huaweiConfigEntity = (HuaweiConfigEntity) arguments[0];
                existConfigEntity.setUsername(huaweiConfigEntity.getUsername());
                existConfigEntity.setPassword(huaweiConfigEntity.getPassword());
            }
            return existConfigEntity;
        });

        HuaweiConfig testConfig = new HuaweiConfig.Builder().setUsername("newUsername").setPassword("newPassword").build();

        // When
        HuaweiConfig result = repository.setHuaweiConfiguration(testConfig);

        // Then
        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
    }
}