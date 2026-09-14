package org.lucoenergia.conluz.domain.shared;

public class PlantProviderCode {

    private final String code;

    public PlantProviderCode(String code) {
        this.code = code;
    }

    public static PlantProviderCode of(String code) {
        return new PlantProviderCode(code);
    }

    public String getCode() {
        return code;
    }
}
