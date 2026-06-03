package org.lucoenergia.conluz.infrastructure.admin.community;

import org.lucoenergia.conluz.domain.admin.community.Community;

import java.util.UUID;

public class CommunityResponse {

    private final UUID id;
    private final String name;
    private final String code;
    private final String legalId;
    private final String address;
    private final Boolean enabled;

    public CommunityResponse(Community community) {
        this.id = community.getId();
        this.name = community.getName();
        this.code = community.getCode();
        this.legalId = community.getLegalId();
        this.address = community.getAddress();
        this.enabled = community.isEnabled();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getLegalId() {
        return legalId;
    }

    public String getAddress() {
        return address;
    }

    public Boolean getEnabled() {
        return enabled;
    }
}
