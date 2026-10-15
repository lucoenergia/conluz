package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.DatadisConfigPasswordRequiredException;
import org.lucoenergia.conluz.domain.datadis.LockDatadisConfigRepository;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfiguration;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetDatadisConfigurationServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final String BASE_URL = "https://datadis.example.com";

    @Mock
    private LockDatadisConfigRepository lockDatadisConfigRepository;
    @Mock
    private GetDatadisConfigRepository getDatadisConfigRepository;
    @Mock
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    private SetDatadisConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new SetDatadisConfigurationServiceImpl(lockDatadisConfigRepository, getDatadisConfigRepository,
                setDatadisConfigurationRepository);
    }

    @Test
    @DisplayName("DCF-001 without a password the stored non-empty password is written back with the other fields")
    void keepsStoredPassword() {
        givenStored(storedConfig("storedPassword"));

        service.setDatadisConfiguration(COMMUNITY_ID, configuration());

        DatadisConfig written = written();
        assertThat(written.getPassword()).isEqualTo("storedPassword");
        assertThat(written.getUsername()).isEqualTo("newUser");
        assertThat(written.getBaseUrl()).isEqualTo(BASE_URL);
    }

    @Test
    @DisplayName("a supplied password is written as given")
    void writesSuppliedPassword() {
        service.setDatadisConfiguration(COMMUNITY_ID, configuration("newPassword"));

        assertThat(written().getPassword()).isEqualTo("newPassword");
    }

    @Test
    @DisplayName("DCF-002 without a password and without a configuration nothing is written")
    void rejectsMissingPasswordWithoutConfig() {
        when(lockDatadisConfigRepository.lock(COMMUNITY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.setDatadisConfiguration(COMMUNITY_ID, configuration()))
                .isInstanceOf(DatadisConfigPasswordRequiredException.class);
        verify(setDatadisConfigurationRepository, never()).setDatadisConfiguration(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("DCF-002 without a password and with a stored null or empty password nothing is written")
    void rejectsMissingPasswordOverStoredBlankPassword(String storedPassword) {
        givenStored(storedConfig(storedPassword));

        assertThatThrownBy(() -> service.setDatadisConfiguration(COMMUNITY_ID, configuration()))
                .isInstanceOf(DatadisConfigPasswordRequiredException.class);
        verify(setDatadisConfigurationRepository, never()).setDatadisConfiguration(any(), any());
    }

    @Test
    @DisplayName("DCF-006 without a stored configuration the write stores it disabled")
    void newConfigurationIsWrittenDisabled() {
        when(lockDatadisConfigRepository.lock(COMMUNITY_ID)).thenReturn(false);

        service.setDatadisConfiguration(COMMUNITY_ID, configuration("newPassword"));

        assertThat(written().getEnabled()).isFalse();
        verify(getDatadisConfigRepository, never()).findByCommunityId(any());
    }

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    @DisplayName("DCF-006 with a stored configuration the write keeps its stored enabled")
    void storedEnabledIsWrittenBack(boolean storedEnabled, boolean passwordSupplied) {
        givenStored(storedConfig("storedPassword", storedEnabled));

        service.setDatadisConfiguration(COMMUNITY_ID,
                passwordSupplied ? configuration("newPassword") : configuration());

        assertThat(written().getEnabled()).isEqualTo(storedEnabled);
    }

    @Test
    @DisplayName("DCF-006 the configuration is locked before it is read, so a concurrent enable or disable is not reverted")
    void locksBeforeReading() {
        givenStored(storedConfig("storedPassword"));

        service.setDatadisConfiguration(COMMUNITY_ID, configuration());

        InOrder order = inOrder(lockDatadisConfigRepository, getDatadisConfigRepository,
                setDatadisConfigurationRepository);
        order.verify(lockDatadisConfigRepository).lock(COMMUNITY_ID);
        order.verify(getDatadisConfigRepository).findByCommunityId(COMMUNITY_ID);
        order.verify(setDatadisConfigurationRepository).setDatadisConfiguration(eq(COMMUNITY_ID), any());
    }

    private void givenStored(DatadisConfig stored) {
        when(lockDatadisConfigRepository.lock(COMMUNITY_ID)).thenReturn(true);
        when(getDatadisConfigRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(Optional.of(stored));
    }

    private DatadisConfig written() {
        ArgumentCaptor<DatadisConfig> captor = ArgumentCaptor.forClass(DatadisConfig.class);
        verify(setDatadisConfigurationRepository).setDatadisConfiguration(eq(COMMUNITY_ID), captor.capture());
        return captor.getValue();
    }

    private static SetDatadisConfiguration configuration() {
        return builder().build();
    }

    private static SetDatadisConfiguration configuration(String password) {
        return builder().withPassword(password).build();
    }

    private static SetDatadisConfiguration.Builder builder() {
        return new SetDatadisConfiguration.Builder()
                .withUsername("newUser")
                .withBaseUrl(BASE_URL);
    }

    private static DatadisConfig storedConfig(String password) {
        return storedConfig(password, true);
    }

    private static DatadisConfig storedConfig(String password, boolean enabled) {
        return new DatadisConfig.Builder()
                .setUsername("storedUser")
                .setPassword(password)
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .setCommunityId(COMMUNITY_ID)
                .build();
    }
}
