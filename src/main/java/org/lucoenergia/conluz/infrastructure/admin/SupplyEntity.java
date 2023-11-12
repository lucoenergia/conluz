package org.lucoenergia.conluz.infrastructure.admin;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "supply")
public class SupplyEntity {

    @Id
    private String id;
    private String name;
    private String address;
    private Float partitionCoefficient;
    private Boolean enabled;

    public SupplyEntity() {
        enabled = true;
    }

    public SupplyEntity(String id, String name, String address, Float partitionCoefficient) {
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

    public Boolean getEnabled() {
        return enabled;
    }
}
