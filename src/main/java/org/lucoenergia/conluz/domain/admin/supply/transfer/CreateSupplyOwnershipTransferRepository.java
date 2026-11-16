package org.lucoenergia.conluz.domain.admin.supply.transfer;

/**
 * Writes the records of supply ownership transfers. There is deliberately no way to change or delete a record: they
 * are deleted only with their supply, by the database (SUP-011).
 */
public interface CreateSupplyOwnershipTransferRepository {

    SupplyOwnershipTransfer create(SupplyOwnershipTransfer transfer);
}
