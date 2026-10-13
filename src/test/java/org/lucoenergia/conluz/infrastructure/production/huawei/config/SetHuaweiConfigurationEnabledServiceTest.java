package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigIncompleteException;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigNotFoundException;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationEnabledService;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
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
class SetHuaweiConfigurationEnabledServiceTest {

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final String BASE_URL = "https://fusionsolar.example.com/thirdData";

    @Mock
    private GetHuaweiConfigRepository getHuaweiConfigRepository;
    @Mock
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;

    private SetHuaweiConfigurationEnabledService service;

    @BeforeEach
    void setUp() {
        service = new SetHuaweiConfigurationEnabledServiceImpl(getHuaweiConfigRepository,
                setHuaweiConfigurationRepository,
                new HuaweiBaseUrlValidityImpl(Validation.buildDefaultValidatorFactory().getValidator()));
    }

    @Test
    @DisplayName("HCF-004 disabling writes only enabled, without reading the stored configuration")
    void disablingWritesOnlyEnabled() {
        when(setHuaweiConfigurationRepository.setEnabled(PLANT_ID, false)).thenReturn(true);

        service.setEnabled(PLANT_ID, false);

        verify(setHuaweiConfigurationRepository).setEnabled(PLANT_ID, false);
        verifyNoMoreInteractions(setHuaweiConfigurationRepository);
        verifyNoInteractions(getHuaweiConfigRepository);
    }

    @Test
    @DisplayName("HCF-004 disabling a plant without a configuration is not found")
    void disablingWithoutConfigurationIsNotFound() {
        when(setHuaweiConfigurationRepository.setEnabled(PLANT_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> service.setEnabled(PLANT_ID, false))
                .isInstanceOf(HuaweiConfigNotFoundException.class);
    }

    @Test
    @DisplayName("HCF-005 enabling a complete configuration writes only enabled")
    void enablingCompleteConfigurationWritesOnlyEnabled() {
        when(getHuaweiConfigRepository.getHuaweiConfig(PLANT_ID))
                .thenReturn(Optional.of(stored("user", BASE_URL, "secret")));
        when(setHuaweiConfigurationRepository.setEnabled(PLANT_ID, true)).thenReturn(true);

        service.setEnabled(PLANT_ID, true);

        verify(setHuaweiConfigurationRepository).setEnabled(PLANT_ID, true);
        verifyNoMoreInteractions(setHuaweiConfigurationRepository);
    }

    @Test
    @DisplayName("HCF-005 enabling a plant without a configuration is not found and writes nothing")
    void enablingWithoutConfigurationIsNotFound() {
        when(getHuaweiConfigRepository.getHuaweiConfig(PLANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEnabled(PLANT_ID, true))
                .isInstanceOf(HuaweiConfigNotFoundException.class);

        verifyNoInteractions(setHuaweiConfigurationRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "baseUrl", "password", "all"})
    @DisplayName("HCF-005 enabling an incomplete configuration lists the missing fields in order and writes nothing")
    void enablingIncompleteConfigurationIsRejected(String missing) {
        boolean all = missing.equals("all");
        when(getHuaweiConfigRepository.getHuaweiConfig(PLANT_ID)).thenReturn(Optional.of(stored(
                all || missing.equals("username") ? " " : "user",
                all || missing.equals("baseUrl") ? "not-a-url" : BASE_URL,
                all || missing.equals("password") ? "" : "secret")));

        assertThatThrownBy(() -> service.setEnabled(PLANT_ID, true))
                .isInstanceOfSatisfying(HuaweiConfigIncompleteException.class, e -> assertThat(e.getMissingFields())
                        .containsExactlyElementsOf(all
                                ? List.of("username", "baseUrl", "password")
                                : List.of(missing)));

        verifyNoInteractions(setHuaweiConfigurationRepository);
    }

    @ParameterizedTest
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#rejected")
    @DisplayName("HCF-005 a base URL the configuration write rejects is reported missing")
    void rejectedBaseUrlIsMissing(String baseUrl) {
        when(getHuaweiConfigRepository.getHuaweiConfig(PLANT_ID))
                .thenReturn(Optional.of(stored("user", baseUrl, "secret")));

        assertThatThrownBy(() -> service.setEnabled(PLANT_ID, true))
                .isInstanceOfSatisfying(HuaweiConfigIncompleteException.class,
                        e -> assertThat(e.getMissingFields()).containsExactly("baseUrl"));
    }

    @ParameterizedTest
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#accepted")
    @DisplayName("HCF-005 a base URL the configuration write accepts is not reported missing")
    void acceptedBaseUrlIsNotMissing(String baseUrl) {
        when(getHuaweiConfigRepository.getHuaweiConfig(PLANT_ID))
                .thenReturn(Optional.of(stored("user", baseUrl, "secret")));
        when(setHuaweiConfigurationRepository.setEnabled(PLANT_ID, true)).thenReturn(true);

        service.setEnabled(PLANT_ID, true);

        verify(setHuaweiConfigurationRepository).setEnabled(PLANT_ID, true);
    }

    private static HuaweiConfig stored(String username, String baseUrl, String password) {
        return new HuaweiConfig.Builder()
                .setUsername(username)
                .setBaseUrl(baseUrl)
                .setPassword(password)
                .setEnabled(Boolean.FALSE)
                .setPlantId(PLANT_ID)
                .build();
    }
}
