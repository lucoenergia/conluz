package org.lucoenergia.conluz.infrastructure.production;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.production.GetInstantProductionService;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production")
public class GetInstantProductionController {

    private final GetInstantProductionService getInstantProductionService;

    public GetInstantProductionController(GetInstantProductionService getInstantProductionService) {
        this.getInstantProductionService = getInstantProductionService;
    }

    @GetMapping
    public InstantProduction getInstantProduction(@RequestParam(required = false) String supplyId) {
        if (StringUtils.isBlank(supplyId)) {
            return getInstantProductionService.getInstantProduction();
        }
        return getInstantProductionService.getInstantProductionBySupply(new SupplyId(supplyId));
    }
}
