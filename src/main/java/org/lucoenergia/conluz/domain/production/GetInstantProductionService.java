package org.lucoenergia.conluz.domain.production;

import org.springframework.stereotype.Service;

@Service
public class GetInstantProductionService {

    private final GetInstantProductionRepository getInstantProductionRepository;

    public GetInstantProductionService(GetInstantProductionRepository getInstantProductionRepository) {
        this.getInstantProductionRepository = getInstantProductionRepository;
    }

    public InstantProduction getInstantProduction() {
        return getInstantProductionRepository.getInstantProduction();
    }
}
