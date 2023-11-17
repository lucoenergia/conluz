package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "users")
public class UserEntity {

    @Id
    private String id;
    private Integer number;
    private String password;
    private String firstName;
    private String lastName;
    private String address;
    private String email;
    private String phoneNumber;
    private Boolean enabled;
    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<SupplyEntity> supplies = new ArrayList<>();

    public UserEntity() {
        enabled = true;
    }

    public UserEntity(String id, Integer number, String password, String firstName, String lastName, String address, String email,
                      String phoneNumber, Boolean enabled) {
        this.id = id;
        this.number = number;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getPassword() {
        return password;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getAddress() {
        return address;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public List<SupplyEntity> getSupplies() {
        return supplies;
    }

    public void addSupply(SupplyEntity supply) {
        supplies.add(supply);
        supply.setUser(this);
    }

    public void removeSupply(SupplyEntity supply) {
        supplies.remove(supply);
        supply.setUser(null);
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }
}
