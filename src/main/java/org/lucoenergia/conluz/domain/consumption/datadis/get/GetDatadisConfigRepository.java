package org.lucoenergia.conluz.domain.consumption.datadis.get;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;

import java.util.Optional;

public interface GetDatadisConfigRepository {

    Optional<DatadisConfig> getDatadisConfig();
}
