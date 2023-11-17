package org.lucoenergia.conluz.infrastructure.admin.user;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class CreateUserBody {

    @NotEmpty(message = "api.users.create.body.id.null")
    private String id;
    @Min(value = 1, message = "api.users.create.body.number.min")
    @NotEmpty(message = "api.users.create.body.number.null")
    private Integer number;
    @NotEmpty(message = "api.users.create.body.firstName.null")
    private String firstName;
    @NotEmpty(message = "api.users.create.body.lastName.null")
    private String lastName;
    @NotEmpty(message = "api.users.create.body.address.null")
    private String address;
    @Email(message = "api.users.create.body.email.invalid")
    @NotEmpty(message = "api.users.create.body.email.null")
    private String email;
    private String phoneNumber;
    @NotEmpty(message = "api.users.create.body.id.null")
    private String password;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
