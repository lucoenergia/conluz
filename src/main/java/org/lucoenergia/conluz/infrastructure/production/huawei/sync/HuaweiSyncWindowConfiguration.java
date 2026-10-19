package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.huawei.sync.HuaweiSyncWindow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HuaweiSyncWindowConfiguration {

    @Bean
    public HuaweiSyncWindow huaweiSyncWindow() {
        return HuaweiSyncWindow.DEFAULT;
    }
}
