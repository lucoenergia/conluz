package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;

import javax.persistence.*;

@Entity(name = "supply")
public class SupplyEntity {

    @Id
    private String id;
    @ManyToOne(fetch = FetchType.LAZY)
    private UserEntity user;
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

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplyEntity )) return false;
        return id != null && id.equals(((SupplyEntity) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
