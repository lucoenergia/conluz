package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;

class SupplyPartitionEntityMapperTest {

    private final UserEntityMapper userEntityMapper = new UserEntityMapper();
    private final SupplyEntityMapper supplyEntityMapper = new SupplyEntityMapper(userEntityMapper);
    private final SharingAgreementEntityMapper sharingAgreementEntityMapper = new SharingAgreementEntityMapper();
    private final SupplyPartitionEntityMapper mapper = new SupplyPartitionEntityMapper(
            supplyEntityMapper, sharingAgreementEntityMapper);

    @Test
    void testMap() {
        // Arrange
        SupplyPartitionEntity entity = SupplyPartitionEntityMother.random();

        // Act
        SupplyPartition result = mapper.map(entity);

        // Assert
        Assertions.assertEquals(entity.getId(), result.getId());
        Assertions.assertEquals(entity.getCoefficient(), result.getCoefficient());
        
        // Verify supply mapping
        Assertions.assertEquals(entity.getSupply().getId(), result.getSupply().getId());
        Assertions.assertEquals(entity.getSupply().getCode(), result.getSupply().getCode());
        
        // Verify sharing agreement mapping
        Assertions.assertEquals(entity.getSharingAgreement().getId(), result.getAgreement().getId());
        Assertions.assertEquals(entity.getSharingAgreement().getStartDate(), result.getAgreement().getStartDate());
        Assertions.assertEquals(entity.getSharingAgreement().getEndDate(), result.getAgreement().getEndDate());
    }
}