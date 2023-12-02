package org.lucoenergia.conluz.domain.admin.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.lucoenergia.conluz.infrastructure.shared.EnvVar;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;


public class User implements UserDetails {

    @NotBlank
    private String id;
    @NotBlank
    private String password;
    @Min(value = 0)
    @NotBlank
    private Integer number;
    @NotBlank
    private String fullName;
    @NotBlank
    private String address;
    @Email
    @NotBlank
    private String email;
    private String phoneNumber;
    private Boolean enabled;
    private Role role;

    public void enable() {
        this.setEnabled(true);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
        return getId();
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

        public Builder id(String id) {
            user.id = id;
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
