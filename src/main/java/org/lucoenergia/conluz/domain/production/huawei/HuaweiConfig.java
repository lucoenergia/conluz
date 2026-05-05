package org.lucoenergia.conluz.domain.production.huawei;

import java.util.UUID;

public class HuaweiConfig {

    public static final String HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT = "huawei_production_realtime";
    public static final String HUAWEI_HOURLY_PRODUCTION_MEASUREMENT = "huawei_production_hourly";
    public static final String HUAWEI_MONTHLY_PRODUCTION_MEASUREMENT = "huawei_production_kwh_month";
    public static final String HUAWEI_YEARLY_PRODUCTION_MEASUREMENT = "huawei_production_kwh_year";

    public static final String DEFAULT_BASE_URL = "https://eu5.fusionsolar.huawei.com/thirdData";

    private UUID id;
    private String username;
    private String password;
    private String baseUrl;
    private Boolean enabled;

    public UUID getId() {
        return id;
    }

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
        private UUID id;
        private String username;
        private String password;
        private String baseUrl;
        private Boolean enabled;

        public Builder setId(UUID id) {
            this.id = id;
            return this;
        }

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

        public HuaweiConfig build() {
            HuaweiConfig huaweiConfig = new HuaweiConfig();
            huaweiConfig.id = this.id;
            huaweiConfig.username = this.username;
            huaweiConfig.password = this.password;
            huaweiConfig.baseUrl = this.baseUrl != null ? this.baseUrl : DEFAULT_BASE_URL;
            huaweiConfig.enabled = this.enabled != null ? this.enabled : Boolean.FALSE;
            return huaweiConfig;
        }
    }
}
