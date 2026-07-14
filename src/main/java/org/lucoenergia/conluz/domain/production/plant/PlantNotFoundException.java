package org.lucoenergia.conluz.domain.production.plant;

import org.lucoenergia.conluz.domain.shared.PlantId;

public class PlantNotFoundException extends RuntimeException {

    private PlantId id;
    private String providerCode;

    public PlantNotFoundException(PlantId id) {
        this.id = id;
    }

    public PlantNotFoundException(String providerCode) {
        this.providerCode = providerCode;
    }

    public PlantId getId() {
        return id;
    }

    public String getProviderCode() {
        return providerCode;
    }
}
