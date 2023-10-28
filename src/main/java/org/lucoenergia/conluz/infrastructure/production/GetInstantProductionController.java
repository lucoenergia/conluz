package org.lucoenergia.conluz.infrastructure.production;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.production.GetProductionService;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production")
public class GetInstantProductionController {

    private final GetProductionService getProductionService;

    public GetInstantProductionController(GetProductionService getProductionService) {
        this.getProductionService = getProductionService;
    }

    @GetMapping
    public InstantProduction getInstantProduction(@RequestParam(required = false) String supplyId) {
        if (StringUtils.isBlank(supplyId)) {
            return getProductionService.getInstantProduction();
        }
        return getProductionService.getInstantProductionBySupply(new SupplyId(supplyId));
    }
}
