package org.lucoenergia.conluz.domain.production.plant.distributorfile;

import java.util.Map;
import java.util.Objects;

/**
 * A single violation found while parsing/validating a distributor file. Carries only domain data
 * (no web-layer type): mapping to a REST error contract is left to the consumer.
 */
public class DistributorFileError {

    private final DistributorFileErrorCode code;
    private final Integer line;
    private final Map<String, String> params;

    public DistributorFileError(DistributorFileErrorCode code, Integer line, Map<String, String> params) {
        this.code = Objects.requireNonNull(code);
        this.line = line;
        this.params = Map.copyOf(params);
    }

    public DistributorFileErrorCode getCode() {
        return code;
    }

    public Integer getLine() {
        return line;
    }

    public Map<String, String> getParams() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DistributorFileError that)) return false;
        return code == that.code && Objects.equals(line, that.line) && Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, line, params);
    }

    @Override
    public String toString() {
        return "DistributorFileError{" +
                "code=" + code +
                ", line=" + line +
                ", params=" + params +
                '}';
    }
}
