package org.lucoenergia.conluz.infrastructure.admin.user.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

@Schema(requiredProperties = {
        "number", "fullName", "personalId", "role"
})
public class UpdateUserBody {

    @NotNull
    @Min(value = 0)
    private Integer number;
    @NotBlank
    private String personalId;
    @NotBlank
    private String fullName;
    private String address;
    @Email
    private String email;
    private String phoneNumber;
    @NotNull
    private Role role;

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getPersonalId() {
        return personalId;
    }

    public void setPersonalId(String personalId) {
        this.personalId = personalId;
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public User toUser(UUID uuid) {
        User user = new User();
        user.setId(uuid);
        user.setPersonalId(this.getPersonalId());
        user.setNumber(this.getNumber());
        user.setFullName(this.getFullName());
        user.setAddress(this.getAddress());
        user.setEmail(this.getEmail());
        user.setPhoneNumber(this.getPhoneNumber());
        user.setRole(this.getRole());
        return user;
    }
}
