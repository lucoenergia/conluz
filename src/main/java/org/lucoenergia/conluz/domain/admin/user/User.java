package org.lucoenergia.conluz.domain.admin.user;

import jakarta.validation.constraints.*;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


public class User implements UserDetails {

    @NotNull
    @ValidUUID
    private UUID id;
    @NotBlank
    private String personalId;
    @NotBlank
    private String password;
    @Min(value = 0)
    @NotNull
    private Integer number;
    @NotBlank
    private String fullName;
    private String address;
    @Email
    @NotBlank
    private String email;
    private String phoneNumber;
    @NotNull
    private Boolean enabled;
    @NotNull
    private Role role;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void initializeUuid() {
        this.setId(UUID.randomUUID());
    }

    public String getPersonalId() {
        return personalId;
    }

    public void setPersonalId(String personalId) {
        this.personalId = personalId;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public void enable() {
        this.setEnabled(true);
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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return getPersonalId();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }


    public static class Builder {
        private final User user;

        public Builder() {
            user = new User();
        }

        public Builder id(UUID uuid) {
            user.id = uuid;
            return this;
        }

        public Builder personalId(String id) {
            user.personalId = id;
            return this;
        }

        public Builder password(String password) {
            user.password = password;
            return this;
        }

        public Builder number(Integer number) {
            user.number = number;
            return this;
        }

        public Builder fullName(String fullName) {
            user.fullName = fullName;
            return this;
        }

        public Builder address(String address) {
            user.address = address;
            return this;
        }

        public Builder email(String email) {
            user.email = email;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            user.phoneNumber = phoneNumber;
            return this;
        }

        public Builder enabled(Boolean enabled) {
            user.enabled = enabled;
            return this;
        }

        public Builder role(Role role) {
            user.role = role;
            return this;
        }

        public User build() {
            return user;
        }
    }
}
