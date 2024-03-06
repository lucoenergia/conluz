package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;

import java.util.UUID;

public class SupplyResponse {

    private final UUID id;
    private final String code;
    private final UserResponse user;
    private final String name;
    private final String address;
    private final Float partitionCoefficient;
    private final Boolean enabled;

    public SupplyResponse(Supply supply) {
        this.id = supply.getId();
        this.code = supply.getCode();
        this.name = supply.getName();
        this.address = supply.getAddress();
        this.partitionCoefficient = supply.getPartitionCoefficient();
        this.enabled = supply.getEnabled();
        this.user = supply.getUser() != null ? new UserResponse(supply.getUser()) : null;
    }

    public UUID getId() {
        return id;
    }
    public String getCode() {
        return code;
    }

    public UserResponse getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public Float getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public Boolean getEnabled() {
        return enabled;
    }
}
