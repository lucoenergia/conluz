package org.lucoenergia.conluz.domain.shared;

import java.util.Objects;

public class SupplyCode {

    private final String code;

    public SupplyCode(String code) {
        this.code = code;
    }

    public static SupplyCode of(String code) {
        return new SupplyCode(code);
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupplyCode that = (SupplyCode) o;
        return Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
