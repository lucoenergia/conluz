package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adds a new supply
 */
@RestController
@RequestMapping("/api/v1/supplies")
public class CreateSupplyController {

    private final CreateSupplyAssembler assembler;
    private final CreateSupplyService service;

    public CreateSupplyController(CreateSupplyAssembler assembler, CreateSupplyService service) {
        this.assembler = assembler;
        this.service = service;
    }

    @PostMapping
    public SupplyResponse createUser(@RequestBody CreateSupplyBody body) {
        Supply newSupply = service.create(assembler.assemble(body), UserId.of(body.getUserId()));
        return new SupplyResponse(newSupply);
    }
}
