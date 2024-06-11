package org.lucoenergia.conluz.infrastructure.admin.user;

import jakarta.persistence.*;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    private String personalId;
    private Integer number;
    private String password;
    private String fullName;
    private String address;
    private String email;
    private String phoneNumber;
    private Boolean enabled = true;
    @Enumerated(EnumType.STRING)
    private Role role;
    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<SupplyEntity> supplies = new ArrayList<>();


    public UUID getId() {
        return id;
    }

    public void setId(UUID uuid) {
        this.id = uuid;
    }

    public String getPersonalId() {
        return personalId;
    }

    public void setPersonalId(String id) {
        this.personalId = id;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String firstName) {
        this.fullName = firstName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
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

    public static UserEntity createNewUser(User user, String encodedPassword) {
        UserEntity entity = new UserEntity();
        entity.setPersonalId(user.getPersonalId());
        entity.setId(user.getId());
        entity.setNumber(user.getNumber());
        entity.setPassword(encodedPassword);
        entity.setFullName(user.getFullName());
        entity.setAddress(user.getAddress());
        entity.setEmail(user.getEmail());
        entity.setPhoneNumber(user.getPhoneNumber());
        entity.setEnabled(user.isEnabled());
        entity.setRole(user.getRole());
        return entity;
    }

    public User getUser() {
        User user = new User();
        user.setId(this.getId());
        user.setPersonalId(this.getPersonalId());
        user.setNumber(this.getNumber());
        user.setPassword(this.getPassword());
        user.setFullName(this.getFullName());
        user.setAddress(this.getAddress());
        user.setEmail(this.getEmail());
        user.setPhoneNumber(this.getPhoneNumber());
        user.setEnabled(this.isEnabled());
        user.setRole(this.getRole());

        return user;
    }
}
