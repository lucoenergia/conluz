package org.lucoenergia.conluz.domain.admin.supply;

public class Supply {

    private final String id;
    private final String name;
    private final String address;
    private final Float partitionCoefficient;
    private final Boolean enabled;

    public Supply(String id, String name, String address, Float partitionCoefficient, Boolean enabled) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.partitionCoefficient = partitionCoefficient;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
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
