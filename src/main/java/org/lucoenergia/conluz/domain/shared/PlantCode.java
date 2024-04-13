package org.lucoenergia.conluz.domain.shared;

public class PlantCode {

    private final String code;

    public PlantCode(String code) {
        this.code = code;
    }

    public static PlantCode of(String code) {
        return new PlantCode(code);
    }

    public String getCode() {
        return code;
    }
}
