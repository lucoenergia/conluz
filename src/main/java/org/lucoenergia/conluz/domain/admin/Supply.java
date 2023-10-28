package org.lucoenergia.conluz.domain.admin;

public class Supply {

    private final String id;
    private final String name;
    private final String address;
    private final Float partitionCoefficient;

    public Supply(String id, String name, String address, Float partitionCoefficient) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.partitionCoefficient = partitionCoefficient;
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
}
