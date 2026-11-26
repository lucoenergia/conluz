package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatadisSyncWindowConfiguration {

    @Bean
    public DatadisSyncWindows datadisSyncWindows() {
        return DatadisSyncWindows.DEFAULT;
    }
}
