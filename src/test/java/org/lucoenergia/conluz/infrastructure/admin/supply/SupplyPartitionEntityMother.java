package org.lucoenergia.conluz.infrastructure.admin.supply;

import java.util.Random;
import java.util.UUID;

public class SupplyPartitionEntityMother {

    public static SupplyPartitionEntity random() {
        return random(SupplyEntityMother.random(), SharingAgreementEntityMother.random());
    }

    public static SupplyPartitionEntity random(SupplyEntity supply, SharingAgreementEntity sharingAgreement) {
        SupplyPartitionEntity entity = new SupplyPartitionEntity();
        entity.setId(UUID.randomUUID());
        entity.setCoefficient(new Random().nextDouble());
        entity.setSupply(supply);
        entity.setSharingAgreement(sharingAgreement);
        return entity;
    }
}