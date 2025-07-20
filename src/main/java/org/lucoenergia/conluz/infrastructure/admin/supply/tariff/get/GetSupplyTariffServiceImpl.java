package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.GetSupplyTariffRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.GetSupplyTariffService;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyAccessHelper;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementation of the SupplyTariffService
 */
@Service
@Transactional(readOnly = true)
public class GetSupplyTariffServiceImpl implements GetSupplyTariffService {

    private final GetSupplyTariffRepository repository;
    private final GetSupplyRepository getSupplyRepository;
    private final SupplyAccessHelper supplyAccessHelper;

    public GetSupplyTariffServiceImpl(
            GetSupplyTariffRepository repository,
            GetSupplyRepository getSupplyRepository,
            SupplyAccessHelper supplyAccessHelper) {
        this.repository = repository;
        this.getSupplyRepository = getSupplyRepository;
        this.supplyAccessHelper = supplyAccessHelper;
    }

    @Override
    public Optional<SupplyTariff> getTariffBySupplyId(final SupplyId supplyId) {
        // Verify that the supply exists
        final Supply supply = getSupplyRepository.findById(supplyId)
                .orElseThrow(() -> new SupplyNotFoundException(supplyId));

        // Verify that the current user is either an admin or the owner of the supply
        if (!supplyAccessHelper.isAdminOrSupplyOwner(supply)) {
            throw new AccessDeniedException("You are not authorized to view tariffs for this supply");
        }

        return repository.findBySupplyId(supplyId);
    }
}
