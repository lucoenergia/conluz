package org.lucoenergia.conluz.domain.admin.user;

public class User {

    private final String id;
    private final Integer number;
    private final String firstName;
    private final String lastName;
    private final String address;
    private final String email;
    private final String phoneNumber;
    private Boolean enabled;

    public User(String id, Integer number, String firstName, String lastName, String address, String email,
                String phoneNumber, Boolean enabled) {
        this.id = id;
        this.number = number;
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.enabled = enabled;
    }

    public User(String id, Integer number, String firstName, String lastName, String address, String email,
                String phoneNumber) {
        this.id = id;
        this.number = number;
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public String getId() {
        return id;
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

    public Integer getNumber() {
        return number;
    }
}
