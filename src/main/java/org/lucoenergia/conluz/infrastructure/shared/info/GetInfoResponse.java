package org.lucoenergia.conluz.infrastructure.shared.info;

public class GetInfoResponse {

    private final String version;

    public GetInfoResponse(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
