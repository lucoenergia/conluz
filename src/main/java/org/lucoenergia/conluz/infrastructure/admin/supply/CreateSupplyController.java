package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.UserId;
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
    public Supply createUser(@RequestBody CreateSupplyBody body) {
        return service.create(assembler.assemble(body), new UserId(body.getUserId()));
    }
}
