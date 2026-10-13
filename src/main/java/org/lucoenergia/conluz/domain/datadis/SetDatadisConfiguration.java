package org.lucoenergia.conluz.domain.datadis;

import java.util.Optional;

/**
 * The settable fields of a community's Datadis configuration. Bundling them here, rather than passing them
 * as individual parameters to {@link SetDatadisConfigurationService}, means adding or removing a field never
 * changes that method signature.
 */
public class SetDatadisConfiguration {

    private final String username;
    private final Optional<String> password;
    private final String baseUrl;
    private final Boolean enabled;

    private SetDatadisConfiguration(Builder builder) {
        this.username = builder.username;
        this.password = builder.password;
        this.baseUrl = builder.baseUrl;
        this.enabled = builder.enabled;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The new password, or empty when none was supplied, in which case the stored password is kept (#404).
     */
    public Optional<String> getPassword() {
        return password;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public static class Builder {
        private String username;
        private Optional<String> password = Optional.empty();
        private String baseUrl;
        private Boolean enabled;

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        /**
         * Supplies a new password. Not calling it leaves the password unsupplied.
         */
        public Builder withPassword(String password) {
            this.password = Optional.of(password);
            return this;
        }

        public Builder withBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder withEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public SetDatadisConfiguration build() {
            return new SetDatadisConfiguration(this);
        }
    }
}
