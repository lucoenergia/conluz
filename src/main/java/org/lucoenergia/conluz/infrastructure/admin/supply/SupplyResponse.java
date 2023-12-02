package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;

public class SupplyResponse {

    private final String id;
    private final UserResponse user;
    private final String name;
    private final String address;
    private final Float partitionCoefficient;
    private final Boolean enabled;

    public SupplyResponse(Supply supply) {
        this.id = supply.getId();
        this.name = supply.getName();
        this.address = supply.getAddress();
        this.partitionCoefficient = supply.getPartitionCoefficient();
        this.enabled = supply.getEnabled();
        this.user = supply.getUser() != null ? new UserResponse(supply.getUser()) : null;
    }

    public String getId() {
        return id;
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
