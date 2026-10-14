package org.lucoenergia.conluz.infrastructure.datadis.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.EnableDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfigIncompleteException;
import org.lucoenergia.conluz.domain.datadis.DatadisConfigNotFoundException;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnableDatadisConfigurationServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final String BASE_URL = "https://datadis.example.com";

    @Mock
    private GetDatadisConfigRepository getDatadisConfigRepository;
    @Mock
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    private EnableDatadisConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new EnableDatadisConfigurationServiceImpl(getDatadisConfigRepository,
                setDatadisConfigurationRepository,
                new DatadisBaseUrlValidityImpl(Validation.buildDefaultValidatorFactory().getValidator()));
    }

    @Test
    @DisplayName("DCF-005 enabling a complete configuration writes only enabled")
    void enablingCompleteConfigurationWritesOnlyEnabled() {
        when(getDatadisConfigRepository.findByCommunityId(COMMUNITY_ID))
                .thenReturn(Optional.of(stored("user", BASE_URL, "secret")));
        when(setDatadisConfigurationRepository.setEnabled(COMMUNITY_ID, true)).thenReturn(true);

        service.enable(COMMUNITY_ID);

        verify(setDatadisConfigurationRepository).setEnabled(COMMUNITY_ID, true);
        verifyNoMoreInteractions(setDatadisConfigurationRepository);
    }

    @Test
    @DisplayName("DCF-005 enabling a community without a configuration is not found and writes nothing")
    void enablingWithoutConfigurationIsNotFound() {
        when(getDatadisConfigRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enable(COMMUNITY_ID))
                .isInstanceOf(DatadisConfigNotFoundException.class);

        verifyNoInteractions(setDatadisConfigurationRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "baseUrl", "password", "all"})
    @DisplayName("DCF-005 enabling an incomplete configuration lists the missing fields in order and writes nothing")
    void enablingIncompleteConfigurationIsRejected(String missing) {
        boolean all = missing.equals("all");
        when(getDatadisConfigRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(Optional.of(stored(
                all || missing.equals("username") ? " " : "user",
                all || missing.equals("baseUrl") ? "not-a-url" : BASE_URL,
                all || missing.equals("password") ? "" : "secret")));

        assertThatThrownBy(() -> service.enable(COMMUNITY_ID))
                .isInstanceOfSatisfying(DatadisConfigIncompleteException.class, e -> assertThat(e.getMissingFields())
                        .containsExactlyElementsOf(all
                                ? List.of("username", "baseUrl", "password")
                                : List.of(missing)));

        verifyNoInteractions(setDatadisConfigurationRepository);
    }

    @ParameterizedTest
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#rejected")
    @DisplayName("DCF-005 a base URL the configuration write rejects is reported missing")
    void rejectedBaseUrlIsMissing(String baseUrl) {
        when(getDatadisConfigRepository.findByCommunityId(COMMUNITY_ID))
                .thenReturn(Optional.of(stored("user", baseUrl, "secret")));

        assertThatThrownBy(() -> service.enable(COMMUNITY_ID))
                .isInstanceOfSatisfying(DatadisConfigIncompleteException.class,
                        e -> assertThat(e.getMissingFields()).containsExactly("baseUrl"));
    }

    @ParameterizedTest
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#accepted")
    @DisplayName("DCF-005 a base URL the configuration write accepts is not reported missing")
    void acceptedBaseUrlIsNotMissing(String baseUrl) {
        when(getDatadisConfigRepository.findByCommunityId(COMMUNITY_ID))
                .thenReturn(Optional.of(stored("user", baseUrl, "secret")));
        when(setDatadisConfigurationRepository.setEnabled(COMMUNITY_ID, true)).thenReturn(true);

        service.enable(COMMUNITY_ID);

        verify(setDatadisConfigurationRepository).setEnabled(COMMUNITY_ID, true);
    }

    private static DatadisConfig stored(String username, String baseUrl, String password) {
        return new DatadisConfig.Builder()
                .setUsername(username)
                .setBaseUrl(baseUrl)
                .setPassword(password)
                .setEnabled(Boolean.FALSE)
                .setCommunityId(COMMUNITY_ID)
                .build();
    }
}
