package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.infrastructure.shared.web.Assembler;
import org.springframework.stereotype.Component;

@Component
public class CreateSupplyAssembler implements Assembler<CreateSupplyBody, Supply> {

    @Override
    public Supply assemble(CreateSupplyBody body) {
        return new Supply(body.getId(), body.getAddress(), body.getPartitionCoefficient(), true);
    }
}
