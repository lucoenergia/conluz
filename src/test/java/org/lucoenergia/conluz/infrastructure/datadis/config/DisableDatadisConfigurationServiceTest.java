package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.datadis.DisableDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfigNotFoundException;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisableDatadisConfigurationServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();

    @Mock
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    private DisableDatadisConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new DisableDatadisConfigurationServiceImpl(setDatadisConfigurationRepository);
    }

    @Test
    @DisplayName("DCF-004 disabling writes only enabled")
    void disablingWritesOnlyEnabled() {
        when(setDatadisConfigurationRepository.setEnabled(COMMUNITY_ID, false)).thenReturn(true);

        service.disable(COMMUNITY_ID);

        verify(setDatadisConfigurationRepository).setEnabled(COMMUNITY_ID, false);
        verifyNoMoreInteractions(setDatadisConfigurationRepository);
    }

    @Test
    @DisplayName("DCF-004 disabling a community without a configuration is not found")
    void disablingWithoutConfigurationIsNotFound() {
        when(setDatadisConfigurationRepository.setEnabled(COMMUNITY_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> service.disable(COMMUNITY_ID))
                .isInstanceOf(DatadisConfigNotFoundException.class);
    }
}
