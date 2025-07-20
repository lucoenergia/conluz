package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.set;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SetSupplyTariffRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SetSupplyTariffService;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyAccessHelper;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class SetSupplyTariffServiceImpl implements SetSupplyTariffService {

    private final SetSupplyTariffRepository repository;
    private final GetSupplyRepository supplyRepository;
    private final SupplyAccessHelper supplyAccessHelper;

    public SetSupplyTariffServiceImpl(
            SetSupplyTariffRepository repository,
            GetSupplyRepository supplyRepository,
            SupplyAccessHelper supplyAccessHelper) {
        this.repository = repository;
        this.supplyRepository = supplyRepository;
        this.supplyAccessHelper = supplyAccessHelper;
    }

    @Override
    public SupplyTariff setTariff(SupplyTariff supplyTariff) {

        final UUID supplyId = supplyTariff.getSupply().getId();

        // Verify that the supply exists
        Supply supply = supplyRepository.findById(SupplyId.of(supplyTariff.getSupply().getId()))
                .orElseThrow(() -> new SupplyNotFoundException(SupplyId.of(supplyId)));

        // Verify that the current user is either an admin or the owner of the supply
        if (!supplyAccessHelper.isAdminOrSupplyOwner(supply)) {
            throw new AccessDeniedException("You are not authorized to set tariffs for this supply");
        }

        // Generate a new UUID if not provided
        if (supplyTariff.getId() == null) {
            UUID id = UUID.randomUUID();
            supplyTariff = new SupplyTariff.Builder()
                    .withId(id)
                    .withSupply(supplyTariff.getSupply())
                    .withValley(supplyTariff.getValley())
                    .withPeak(supplyTariff.getPeak())
                    .withOffPeak(supplyTariff.getOffPeak())
                    .build();
        }

        return repository.save(supplyTariff);
    }
}