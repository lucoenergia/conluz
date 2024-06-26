package org.lucoenergia.conluz.infrastructure.production.get;

import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.springframework.stereotype.Component;

@Component
public class InstantProductionInfluxMapper extends BaseMapper<ProductionPoint, InstantProduction> {

    public InstantProduction map(ProductionPoint point) {
        return new InstantProduction(point.getInverterPower());
    }
}
