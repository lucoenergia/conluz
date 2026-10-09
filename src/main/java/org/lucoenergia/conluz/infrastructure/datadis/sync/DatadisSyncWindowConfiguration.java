package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatadisSyncWindowConfiguration {

    @Bean
    public DatadisSyncWindow datadisSyncWindow() {
        return DatadisSyncWindow.DEFAULT;
    }
}
