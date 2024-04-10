package org.lucoenergia.conluz.domain.production.huawei;

import java.util.UUID;

public class HuaweiConfig {

    public static final String HUAWEI_INSTANT_PRODUCTION_MEASUREMENT = "huawei_production_realtime_kwh";

    public static final String BASE_URL = "https://eu5.fusionsolar.huawei.com/thirdData";

    private UUID id;
    private String username;
    private String password;

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public static class Builder {
        private UUID id;
        private String username;
        private String password;

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

        public HuaweiConfig build() {
            HuaweiConfig huaweiConfig = new HuaweiConfig();
            huaweiConfig.id = this.id;
            huaweiConfig.username = this.username;
            huaweiConfig.password = this.password;
            return huaweiConfig;
        }
    }
}
