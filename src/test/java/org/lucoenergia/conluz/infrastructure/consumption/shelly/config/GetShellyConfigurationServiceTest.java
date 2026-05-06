package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConfigRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetShellyConfigurationServiceTest {

    private final GetShellyConfigRepository getShellyConfigRepository = mock(GetShellyConfigRepository.class);
    private final GetShellyConfigurationServiceImpl service = new GetShellyConfigurationServiceImpl(getShellyConfigRepository);

    @Test
    void isDisabled_noConfigFound_returnsTrue() {
        when(getShellyConfigRepository.getShellyConfig()).thenReturn(Optional.empty());

        assertTrue(service.isDisabled());
    }

    @Test
    void isDisabled_configExistsButDisabled_returnsTrue() {
        ShellyConfig config = new ShellyConfig.Builder()
                .setEnabled(Boolean.FALSE)
                .build();
        when(getShellyConfigRepository.getShellyConfig()).thenReturn(Optional.of(config));

        assertTrue(service.isDisabled());
    }

    @Test
    void isDisabled_configExistsAndEnabled_returnsFalse() {
        ShellyConfig config = new ShellyConfig.Builder()
                .setEnabled(Boolean.TRUE)
                .build();
        when(getShellyConfigRepository.getShellyConfig()).thenReturn(Optional.of(config));

        assertFalse(service.isDisabled());
    }
}
