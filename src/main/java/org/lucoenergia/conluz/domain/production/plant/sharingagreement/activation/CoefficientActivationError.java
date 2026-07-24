package org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation;

import java.util.Map;
import java.util.Objects;

/**
 * A single per-item violation found while validating an activation batch. Carries only domain data
 * (no web-layer type); mapping to the REST error contract is left to the consumer.
 */
public class CoefficientActivationError {

    private final CoefficientActivationErrorCode code;
    private final Map<String, String> params;

    public CoefficientActivationError(CoefficientActivationErrorCode code, Map<String, String> params) {
        this.code = Objects.requireNonNull(code);
        this.params = Map.copyOf(params);
    }

    public CoefficientActivationErrorCode getCode() {
        return code;
    }

    public Map<String, String> getParams() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CoefficientActivationError that)) return false;
        return code == that.code && Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, params);
    }

    @Override
    public String toString() {
        return "CoefficientActivationError{code=" + code + ", params=" + params + '}';
    }
}
