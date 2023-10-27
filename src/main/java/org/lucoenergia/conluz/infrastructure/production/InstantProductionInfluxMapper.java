package org.lucoenergia.conluz.infrastructure.production;

import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.springframework.stereotype.Component;

@Component
public class InstantProductionInfluxMapper {

    public InstantProduction map(InstantProductionPoint measurement) {
        return new InstantProduction(measurement.getInverterPower());
    }
}
