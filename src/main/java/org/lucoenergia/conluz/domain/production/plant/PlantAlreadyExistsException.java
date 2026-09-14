package org.lucoenergia.conluz.domain.production.plant;

import org.lucoenergia.conluz.domain.shared.PlantProviderCode;

public class PlantAlreadyExistsException extends RuntimeException {

    private final PlantProviderCode providerCode;

    public PlantAlreadyExistsException(PlantProviderCode providerCode) {
        this.providerCode = providerCode;
    }

    public PlantProviderCode getProviderCode() {
        return providerCode;
    }
}
