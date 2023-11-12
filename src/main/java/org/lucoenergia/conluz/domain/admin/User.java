package org.lucoenergia.conluz.domain.admin;

public class User {

    private final String id;
    private final String firstName;
    private final String lastName;
    private final String address;
    private final String email;
    private final String phoneNumber;
    private final Boolean enabled;

    public User(String id, String firstName, String lastName, String address, String email,
                String phoneNumber, Boolean enabled) {
        this.id = id;
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
