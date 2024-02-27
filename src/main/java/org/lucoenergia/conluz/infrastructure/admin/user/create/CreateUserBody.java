package org.lucoenergia.conluz.infrastructure.admin.user.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;

@Schema(requiredProperties = {
        "personalId", "number", "fullName", "email", "password", "role"
})
public class CreateUserBody {

    @NotBlank
    private String personalId;
    @NotNull
    @Min(value = 0)
    private Integer number;
    @NotBlank
    private String fullName;
    private String address;
    @NotBlank
    @Email
    private String email;
    private String phoneNumber;
    @NotBlank
    private String password;
    @NotNull
    private Role role;

    public String getPersonalId() {
        return personalId;
    }

    public void setPersonalId(String personalId) {
        this.personalId = personalId;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public User mapToUser() {
        User user = new User();
        user.setPersonalId(this.getPersonalId());
        user.setNumber(this.getNumber());
        user.setPassword(this.getPassword());
        user.setFullName(this.getFullName());
        user.setAddress(this.getAddress());
        user.setEmail(this.getEmail());
        user.setPhoneNumber(this.getPhoneNumber());
        user.setRole(this.getRole());
        return user;
    }

    @Override
    public String toString() {
        return "CreateUserBody{" +
                "personalId='" + personalId + '\'' +
                ", number=" + number +
                ", fullName='" + fullName + '\'' +
                ", address='" + address + '\'' +
                ", email='" + email + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", role=" + role +
                '}';
    }
}
