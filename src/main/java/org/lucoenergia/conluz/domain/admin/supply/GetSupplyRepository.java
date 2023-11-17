package org.lucoenergia.conluz.domain.admin.supply;

import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.Optional;

public interface GetSupplyRepository {

    Optional<Supply> findById(SupplyId id);
}