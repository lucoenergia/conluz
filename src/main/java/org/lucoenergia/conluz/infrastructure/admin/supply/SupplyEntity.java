package org.lucoenergia.conluz.infrastructure.admin.supply;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.admin.supply.contract.SupplyContractEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.datadis.SupplyDatadisEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.distributor.SupplyDistributorEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.shelly.SupplyShellyEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "supplies")
public class SupplyEntity {

    @Id
    private UUID id;
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserEntity user;

    private String name;
    private String address;
    private String addressRef;
    private Float partitionCoefficient;
    private Boolean enabled;

    @OneToOne(mappedBy = "supply", cascade = CascadeType.ALL, orphanRemoval = true)
    private SupplyShellyEntity shelly;

    @OneToOne(mappedBy = "supply", cascade = CascadeType.ALL, orphanRemoval = true)
    private SupplyDatadisEntity datadis;

    @OneToOne(mappedBy = "supply", cascade = CascadeType.ALL, orphanRemoval = true)
    private SupplyDistributorEntity distributor;

    @OneToOne(mappedBy = "supply", cascade = CascadeType.ALL, orphanRemoval = true)
    private SupplyContractEntity contract;

    @OneToMany(
            mappedBy = "supply",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PlantEntity> plants = new ArrayList<>();

    @OneToMany(
            mappedBy = "supply",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<SupplyPartitionEntity> partitions;

    public SupplyEntity() {
        enabled = true;
    }

    public static class Builder {
        private UUID id;
        private String code;
        private UserEntity user;
        private String name;
        private String address;
        private String addressRef;
        private Float partitionCoefficient;
        private Boolean enabled;
        private SupplyShellyEntity shelly;
        private SupplyDatadisEntity datadis;
        private SupplyDistributorEntity distributor;
        private SupplyContractEntity contract;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withCode(String code) {
            this.code = code;
            return this;
        }

        public Builder withUser(UserEntity user) {
            this.user = user;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder withAddressRef(String addressRef) {
            this.addressRef = addressRef;
            return this;
        }

        public Builder withPartitionCoefficient(Float partitionCoefficient) {
            this.partitionCoefficient = partitionCoefficient;
            return this;
        }

        public Builder withEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder withShelly(SupplyShellyEntity shelly) {
            this.shelly = shelly;
            return this;
        }

        public Builder withDatadis(SupplyDatadisEntity datadis) {
            this.datadis = datadis;
            return this;
        }

        public Builder withDistributor(SupplyDistributorEntity distributor) {
            this.distributor = distributor;
            return this;
        }

        public Builder withContract(SupplyContractEntity contract) {
            this.contract = contract;
            return this;
        }

        public SupplyEntity build() {
            SupplyEntity entity = new SupplyEntity();
            entity.id = this.id;
            entity.code = this.code;
            entity.user = this.user;
            entity.name = this.name;
            entity.address = this.address;
            entity.addressRef = this.addressRef;
            entity.partitionCoefficient = this.partitionCoefficient;
            entity.enabled = this.enabled;
            if (shelly != null) {
                entity.shelly = shelly;
                shelly.setSupply(entity);
            }
            if (datadis != null) {
                entity.datadis = datadis;
                datadis.setSupply(entity);
            }
            if (distributor != null) {
                entity.distributor = distributor;
                distributor.setSupply(entity);
            }
            if (contract != null) {
                entity.contract = contract;
                contract.setSupply(entity);
            }
            return entity;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplyEntity )) return false;
        return code != null && code.equals(((SupplyEntity) o).getCode());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getAddressRef() {
        return addressRef;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setAddressRef(String addressRef) {
        this.addressRef = addressRef;
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

    public SupplyShellyEntity getShelly() {
        return shelly;
    }

    public void setShelly(SupplyShellyEntity shelly) {
        this.shelly = shelly;
        if (shelly != null) {
            shelly.setSupply(this);
        }
    }

    public SupplyDatadisEntity getDatadis() {
        return datadis;
    }

    public void setDatadis(SupplyDatadisEntity datadis) {
        this.datadis = datadis;
        if (datadis != null) {
            datadis.setSupply(this);
        }
    }

    public SupplyDistributorEntity getDistributor() {
        return distributor;
    }

    public void setDistributor(SupplyDistributorEntity distributor) {
        this.distributor = distributor;
        if (distributor != null) {
            distributor.setSupply(this);
        }
    }

    public SupplyContractEntity getContract() {
        return contract;
    }

    public void setContract(SupplyContractEntity contract) {
        this.contract = contract;
        if (contract != null) {
            contract.setSupply(this);
        }
    }

    public List<PlantEntity> getPlants() {
        return plants;
    }

    public void addPlant(PlantEntity plant) {
        plants.add(plant);
        plant.setSupply(this);
    }

    public void removePlant(PlantEntity plant) {
        plants.remove(plant);
        plant.setSupply(null);
    }

    public List<SupplyPartitionEntity> getPartitions() {
        return partitions;
    }

    public void addPartition(SupplyPartitionEntity partition) {
        partitions.add(partition);
        partition.setSupply(this);
    }

    public void removePartition(SupplyPartitionEntity partition) {
        partitions.remove(partition);
        partition.setSupply(null);
    }
}
