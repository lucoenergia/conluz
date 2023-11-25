package org.lucoenergia.conluz.infrastructure.admin.supply;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public class CreateSupplyBody {

    @NotEmpty
    private String id;
    @NotEmpty
    private String userId;
    @NotEmpty
    private String address;
    @Positive
    private Float partitionCoefficient;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Float getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public void setPartitionCoefficient(Float partitionCoefficient) {
        this.partitionCoefficient = partitionCoefficient;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
