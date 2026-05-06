package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.config.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class GetDatadisConfigurationServiceTest {

    private final GetDatadisConfigRepository getDatadisConfigRepository = Mockito.mock(GetDatadisConfigRepository.class);
    private final GetDatadisConfigurationService service = new GetDatadisConfigurationServiceImpl(getDatadisConfigRepository);

    @Test
    void testIsDisabled_WhenNoConfigExists() {
        when(getDatadisConfigRepository.getDatadisConfig()).thenReturn(Optional.empty());

        assertTrue(service.isDisabled());
    }

    @Test
    void testIsDisabled_WhenEnabledIsFalse() {
        DatadisConfig config = new DatadisConfig.Builder()
                .setUsername("user")
                .setPassword("pass")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.FALSE)
                .build();
        when(getDatadisConfigRepository.getDatadisConfig()).thenReturn(Optional.of(config));

        assertTrue(service.isDisabled());
    }

    @Test
    void testIsDisabled_WhenEnabledIsTrue() {
        DatadisConfig config = new DatadisConfig.Builder()
                .setUsername("user")
                .setPassword("pass")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build();
        when(getDatadisConfigRepository.getDatadisConfig()).thenReturn(Optional.of(config));

        assertFalse(service.isDisabled());
    }
}
