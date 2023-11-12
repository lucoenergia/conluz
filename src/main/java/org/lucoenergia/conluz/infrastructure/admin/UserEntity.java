package org.lucoenergia.conluz.infrastructure.admin;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "users")
public class UserEntity {

    @Id
    private String id;
    private String password;
    private String firstName;
    private String lastName;
    private String address;
    private String email;
    private String phoneNumber;
    private Boolean enabled;

    public UserEntity() {
        enabled = true;
    }

    public UserEntity(String id, String password, String firstName, String lastName, String address, String email,
                      String phoneNumber, Boolean enabled) {
        this.id = id;
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
}
