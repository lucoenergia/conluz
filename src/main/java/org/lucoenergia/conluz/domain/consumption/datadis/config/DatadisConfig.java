package org.lucoenergia.conluz.domain.consumption.datadis.config;

public class DatadisConfig {

    public static final String DEFAULT_BASE_URL = "https://datadis.es";

    private String username;
    private String password;
    private String baseUrl;
    private Boolean enabled;

    public String getUsername() {
        return username;
    }

    public String getPassword() {
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
        private String password;
        private String baseUrl;
        private Boolean enabled;

        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder setPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder setEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public DatadisConfig build() {
            DatadisConfig config = new DatadisConfig();
            config.username = this.username;
            config.password = this.password;
            config.baseUrl = this.baseUrl != null ? this.baseUrl : DEFAULT_BASE_URL;
            config.enabled = this.enabled != null ? this.enabled : Boolean.FALSE;
            return config;
        }
    }
}
