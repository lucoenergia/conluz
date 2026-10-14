package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.huawei.config.DisableHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigNotFoundException;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisableHuaweiConfigurationServiceTest {

    private static final UUID PLANT_ID = UUID.randomUUID();

    @Mock
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;

    private DisableHuaweiConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new DisableHuaweiConfigurationServiceImpl(setHuaweiConfigurationRepository);
    }

    @Test
    @DisplayName("HCF-004 disabling writes only enabled")
    void disablingWritesOnlyEnabled() {
        when(setHuaweiConfigurationRepository.setEnabled(PLANT_ID, false)).thenReturn(true);

        service.disable(PLANT_ID);

        verify(setHuaweiConfigurationRepository).setEnabled(PLANT_ID, false);
        verifyNoMoreInteractions(setHuaweiConfigurationRepository);
    }

    @Test
    @DisplayName("HCF-004 disabling a plant without a configuration is not found")
    void disablingWithoutConfigurationIsNotFound() {
        when(setHuaweiConfigurationRepository.setEnabled(PLANT_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> service.disable(PLANT_ID))
                .isInstanceOf(HuaweiConfigNotFoundException.class);
    }
}
