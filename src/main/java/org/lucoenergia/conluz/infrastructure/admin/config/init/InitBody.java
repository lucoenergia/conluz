package org.lucoenergia.conluz.infrastructure.admin.config.init;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.user.DefaultAdminUser;

public class InitBody {

    private CreateDefaultAdminUserBody defaultAdminUser;

    public CreateDefaultAdminUserBody getDefaultAdminUser() {
        return defaultAdminUser;
    }

    public void setDefaultAdminUser(CreateDefaultAdminUserBody defaultAdminUser) {
        this.defaultAdminUser = defaultAdminUser;
    }

    public DefaultAdminUser toDefaultAdminUserDomain() {
        DefaultAdminUser user = new DefaultAdminUser();
        user.setPersonalId(this.defaultAdminUser.personalId);
        user.setPassword(this.defaultAdminUser.password);
        user.setFullName(this.defaultAdminUser.fullName);
        user.setAddress(this.defaultAdminUser.address);
        user.setEmail(this.defaultAdminUser.email);
        return user;
    }

    public static class CreateDefaultAdminUserBody {

        @Schema(description = "The default admin's DNI/NIE/NIF, used as login username. It is normalised before it is stored or compared: whitespace (including the no-break space), dots and hyphens are removed and letters are upper-cased, so 12.345.678-a and 12345678A are the same identifier.")
        private String personalId;
        private String fullName;
        private String address;
        private String email;
        private String password;

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

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
