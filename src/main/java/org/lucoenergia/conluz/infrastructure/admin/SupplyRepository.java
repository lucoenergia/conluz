package org.lucoenergia.conluz.infrastructure.admin;

import org.lucoenergia.conluz.infrastructure.production.SupplyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyRepository extends JpaRepository<SupplyEntity, String> {
}
