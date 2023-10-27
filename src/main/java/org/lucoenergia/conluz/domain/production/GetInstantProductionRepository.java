package org.lucoenergia.conluz.domain.production;

public interface GetInstantProductionRepository {

    InstantProduction getInstantProduction();

    InstantProduction getInstantProductionBySupply();
}
