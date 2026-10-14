package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import jakarta.validation.Validator;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiBaseUrlValidity;
import org.springframework.stereotype.Component;

/**
 * Runs the constraints declared on {@link ConfigureHuaweiBody#getBaseUrl()}, so the verdict is the configuration
 * write's by construction rather than a second definition of a valid URL.
 */
@Component
public class HuaweiBaseUrlValidityImpl implements HuaweiBaseUrlValidity {

    private final Validator validator;

    public HuaweiBaseUrlValidityImpl(Validator validator) {
        this.validator = validator;
    }

    @Override
    public boolean isValid(String baseUrl) {
        return validator.validateValue(ConfigureHuaweiBody.class, "baseUrl", baseUrl).isEmpty();
    }
}
