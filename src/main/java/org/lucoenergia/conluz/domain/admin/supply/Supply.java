package org.lucoenergia.conluz.domain.admin.supply;

import org.lucoenergia.conluz.domain.admin.user.User;

public class Supply {

    private final String id;
    private User user;
    private String name;
    private final String address;
    private final Float partitionCoefficient;
    private final Boolean enabled;

    public Supply(String id, String address, Float partitionCoefficient, Boolean enabled) {
        this.id = id;
        this.address = address;
        this.partitionCoefficient = partitionCoefficient;
        this.enabled = enabled;
    }

    public Supply(String id, String name, String address, Float partitionCoefficient, Boolean enabled) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.partitionCoefficient = partitionCoefficient;
        this.enabled = enabled;
    }

    public Supply(String id, User user, String name, String address, Float partitionCoefficient, Boolean enabled) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.address = address;
        this.partitionCoefficient = partitionCoefficient;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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
