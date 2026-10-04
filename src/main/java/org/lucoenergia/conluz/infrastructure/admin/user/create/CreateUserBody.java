package org.lucoenergia.conluz.infrastructure.admin.user.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

@Schema(requiredProperties = {
        "personalId", "number", "fullName", "email", "password"
})
public class CreateUserBody {

    @Schema(description = "The user's DNI/NIE/NIF, unique among users. It is normalised before it is stored or compared: whitespace (including the no-break space), dots and hyphens are removed and letters are upper-cased, so 12.345.678-a and 12345678A are the same identifier.")
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
    @NotNull
    @Schema(minLength = 15, description = """
            The initial password. Between 15 and 64 characters, counting each Unicode code point as one, and no \
            more than 72 bytes once UTF-8 encoded. Any character is accepted, including spaces and non-ASCII \
            letters; there are no composition rules, and the value is never trimmed or transformed. The new user \
            is flagged as having to change it.""")
    private String password;

    private UUID communityId;

    private CommunityRole communityRole;

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

    public UUID getCommunityId() {
        return communityId;
    }

    public void setCommunityId(UUID communityId) {
        this.communityId = communityId;
    }

    public CommunityRole getCommunityRole() {
        return communityRole;
    }

    public void setCommunityRole(CommunityRole communityRole) {
        this.communityRole = communityRole;
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
                ", communityId=" + communityId +
                ", communityRole=" + communityRole +
                '}';
    }
}
