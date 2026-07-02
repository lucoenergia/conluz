package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

class SetDatadisConfigurationRepositoryDatabaseTest {

    private final DatadisConfigRepository datadisConfigRepository = Mockito.mock(DatadisConfigRepository.class);
    private final CommunityJpaRepository communityJpaRepository = Mockito.mock(CommunityJpaRepository.class);
    private final SetDatadisConfigurationRepositoryDatabase repository =
            new SetDatadisConfigurationRepositoryDatabase(datadisConfigRepository, communityJpaRepository);

    @Test
    void testSetDatadisConfiguration_notExist() {
        when(datadisConfigRepository.findFirstBy()).thenReturn(Optional.empty());
        when(datadisConfigRepository.save(any(DatadisConfigEntity.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        DatadisConfig testConfig = new DatadisConfig.Builder()
                .setUsername("username1")
                .setPassword("password1")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build();

        DatadisConfig result = repository.setDatadisConfiguration(testConfig);

        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
        assertEquals(testConfig.getBaseUrl(), result.getBaseUrl());
        assertEquals(testConfig.getEnabled(), result.getEnabled());
    }

    @Test
    void testSetDatadisConfiguration_exist() {
        DatadisConfigEntity existConfigEntity = new DatadisConfigEntity();
        existConfigEntity.setId(UUID.randomUUID());
        existConfigEntity.setUsername("username2");
        existConfigEntity.setPassword("password2");
        existConfigEntity.setBaseUrl(DatadisConfig.DEFAULT_BASE_URL);
        existConfigEntity.setEnabled(Boolean.FALSE);

        when(datadisConfigRepository.findFirstBy()).thenReturn(Optional.of(existConfigEntity));
        when(datadisConfigRepository.save(any(DatadisConfigEntity.class))).thenAnswer(invocation -> {
            DatadisConfigEntity e = (DatadisConfigEntity) invocation.getArguments()[0];
            existConfigEntity.setUsername(e.getUsername());
            existConfigEntity.setPassword(e.getPassword());
            existConfigEntity.setBaseUrl(e.getBaseUrl());
            existConfigEntity.setEnabled(e.getEnabled());
            return existConfigEntity;
        });

        DatadisConfig testConfig = new DatadisConfig.Builder()
                .setUsername("newUsername")
                .setPassword("newPassword")
                .setBaseUrl("http://localhost:8080")
                .setEnabled(Boolean.TRUE)
                .build();

        DatadisConfig result = repository.setDatadisConfiguration(testConfig);

        assertEquals(testConfig.getUsername(), result.getUsername());
        assertEquals(testConfig.getPassword(), result.getPassword());
        assertEquals(testConfig.getBaseUrl(), result.getBaseUrl());
        assertEquals(testConfig.getEnabled(), result.getEnabled());
    }
}
