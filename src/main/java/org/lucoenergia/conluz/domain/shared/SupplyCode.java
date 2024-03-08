package org.lucoenergia.conluz.domain.shared;

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
}
