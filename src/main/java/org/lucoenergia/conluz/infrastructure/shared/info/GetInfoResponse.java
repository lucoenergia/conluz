package org.lucoenergia.conluz.infrastructure.shared.info;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"version"})
public class GetInfoResponse {

    private final String version;

    public GetInfoResponse(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
