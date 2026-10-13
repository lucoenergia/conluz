package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The verdict is the one the configuration write's body constraints give the same string.
 */
class HuaweiBaseUrlValidityImplTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final HuaweiBaseUrlValidityImpl validity = new HuaweiBaseUrlValidityImpl(VALIDATOR);

    @ParameterizedTest
    @NullSource
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#rejected")
    void rejectsWhatTheConfigurationWriteRejects(String baseUrl) {
        assertThat(bodyAccepts(baseUrl)).isFalse();
        assertThat(validity.isValid(baseUrl)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#accepted")
    void acceptsWhatTheConfigurationWriteAccepts(String baseUrl) {
        assertThat(bodyAccepts(baseUrl)).isTrue();
        assertThat(validity.isValid(baseUrl)).isTrue();
    }

    private static boolean bodyAccepts(String baseUrl) {
        ConfigureHuaweiBody body = new ConfigureHuaweiBody("user", "secret", baseUrl, Boolean.TRUE);
        return VALIDATOR.validateProperty(body, "baseUrl").isEmpty();
    }
}
