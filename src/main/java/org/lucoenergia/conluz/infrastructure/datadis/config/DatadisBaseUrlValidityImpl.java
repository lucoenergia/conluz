package org.lucoenergia.conluz.infrastructure.datadis.config;

import jakarta.validation.Validator;
import org.lucoenergia.conluz.domain.datadis.DatadisBaseUrlValidity;
import org.springframework.stereotype.Component;

/**
 * Runs the constraints declared on {@link ConfigureDatadisBody#getBaseUrl()}, so the verdict is the configuration
 * write's by construction rather than a second definition of a valid URL.
 */
@Component
public class DatadisBaseUrlValidityImpl implements DatadisBaseUrlValidity {

    private final Validator validator;

    public DatadisBaseUrlValidityImpl(Validator validator) {
        this.validator = validator;
    }

    @Override
    public boolean isValid(String baseUrl) {
        return validator.validateValue(ConfigureDatadisBody.class, "baseUrl", baseUrl).isEmpty();
    }
}
