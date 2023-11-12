package org.lucoenergia.conluz.infrastructure.admin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.Supply;

public class SupplyEntityMapperTest {

    private final SupplyEntityMapper mapper = new SupplyEntityMapper();

    @Test
    void testMap() {
        SupplyEntity entity = new SupplyEntity("XX000012345678901234F0RT", "My supply",
                "Fake Street 123", 3.1245f);

        Supply result = mapper.map(entity);

        Assertions.assertEquals(entity.getId(), result.getId());
        Assertions.assertEquals(entity.getName(), result.getName());
        Assertions.assertEquals(entity.getAddress(), result.getAddress());
        Assertions.assertEquals(entity.getPartitionCoefficient(), result.getPartitionCoefficient());
        Assertions.assertEquals(entity.getEnabled(), result.getEnabled());
    }
}
