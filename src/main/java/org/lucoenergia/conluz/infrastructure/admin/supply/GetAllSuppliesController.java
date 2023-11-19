package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Get all supplies registered in the energy community
 */
@RestController
@RequestMapping("/api/v1/supplies")
public class GetAllSuppliesController {

    private final GetSupplyService service;

    public GetAllSuppliesController(GetSupplyService service) {
        this.service = service;
    }


    @GetMapping
    public PagedResult<Supply> getAllSupplies(PagedRequest page) {
        return service.findAll(page);
    }
}
