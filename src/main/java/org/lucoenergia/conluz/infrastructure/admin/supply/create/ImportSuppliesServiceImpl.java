package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportSuppliesService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportOutcome;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportRow;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Not transactional itself: each row is created in a transaction of its own by {@link CreateSupplyService}, so a row
 * that fails keeps the rows created before and after it.
 */
@Service
public class ImportSuppliesServiceImpl implements ImportSuppliesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportSuppliesServiceImpl.class);

    private final CreateSupplyService createSupplyService;

    public ImportSuppliesServiceImpl(CreateSupplyService createSupplyService) {
        this.createSupplyService = createSupplyService;
    }

    @Override
    public List<ImportedSupplyRow> importSupplies(UUID communityId, List<SupplyImportRow> rows) {
        List<ImportedSupplyRow> results = new ArrayList<>();
        for (SupplyImportRow row : rows) {
            results.add(importRow(communityId, row));
        }
        return results;
    }

    private ImportedSupplyRow importRow(UUID communityId, SupplyImportRow row) {
        try {
            Supply newSupply = createSupplyService.create(row.supply(), UserPersonalId.of(row.personalId()),
                    communityId);
            return new ImportedSupplyRow(newSupply.getCode(), row.personalId(), SupplyImportOutcome.CREATED);
        } catch (SupplyAlreadyExistsException e) {
            LOGGER.error("Supply already exists", e);
            return new ImportedSupplyRow(row.code(), row.personalId(), SupplyImportOutcome.ALREADY_EXISTS);
        } catch (SupplyOwnerNotFoundException e) {
            LOGGER.error("User not found", e);
            return new ImportedSupplyRow(row.code(), row.personalId(), SupplyImportOutcome.USER_NOT_FOUND);
        } catch (Exception e) {
            LOGGER.error("Unable to create supply", e);
            return new ImportedSupplyRow(row.code(), row.personalId(), SupplyImportOutcome.FAILED);
        }
    }
}
