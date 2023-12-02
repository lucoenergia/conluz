package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public PagedResult<SupplyResponse> getAllSupplies(PagedRequest page) {
        PagedResult<Supply> supplies = service.findAll(page);

        List<SupplyResponse> suppliesResponse = supplies.getItems().stream()
                .map(SupplyResponse::new)
                .toList();

        return new PagedResult<>(suppliesResponse, supplies.getSize(), supplies.getTotalElements(),
                supplies.getTotalPages(), supplies.getNumber());
    }
}
