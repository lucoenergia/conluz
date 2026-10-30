package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyRegisteredInAnotherCommunityException;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportSuppliesService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplies;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow.ColumnViolation;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportConstraint;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportFileProblem;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportFileRefusedException;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportRowOutcome;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFile;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFileReader;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFileRefusedException;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportLayout;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Not transactional itself: each row is created in a transaction of its own by {@link CreateSupplyService}, so a row
 * that fails keeps the rows created before and after it, and the owner's lock is held for one row at a time.
 *
 * <p>A row is checked for empty required values and for a code that is not a CUPS before it is created, and every
 * rule it breaks is reported at once; then it is created as {@code createSupply} creates a supply, which checks the
 * owner and the CUPS's uniqueness. A broken rule is reported as its {@link SupplyImportConstraint}, never with a
 * value of the row.</p>
 */
@Service
public class ImportSuppliesServiceImpl implements ImportSuppliesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportSuppliesServiceImpl.class);

    static final String CODE = "code";
    static final String ADDRESS = "address";
    static final String PERSONAL_ID = "personalId";
    static final String ADDRESS_REF = "addressRef";
    static final String NAME = "name";
    static final List<String> COLUMNS = List.of(CODE, ADDRESS, PERSONAL_ID, ADDRESS_REF, NAME);
    static final List<String> REQUIRED_COLUMNS = List.of(CODE, ADDRESS, PERSONAL_ID);

    private static final CsvImportLayout LAYOUT = new CsvImportLayout(COLUMNS, REQUIRED_COLUMNS, MAX_ROWS);

    private final CsvImportFileReader reader;
    private final CreateSupplyService createSupplyService;

    public ImportSuppliesServiceImpl(CsvImportFileReader reader, CreateSupplyService createSupplyService) {
        this.reader = reader;
        this.createSupplyService = createSupplyService;
    }

    @Override
    public ImportedSupplies importSupplies(UUID communityId, byte[] csv) {
        CsvImportFile file;
        try {
            file = reader.read(csv, LAYOUT);
        } catch (CsvImportFileRefusedException e) {
            // Without the headers: a file without a header line holds a supply's data in its first line
            LOGGER.info("Supply import refused: communityId={}, reason={}", communityId, e.getProblem());
            throw new SupplyImportFileRefusedException(problemOf(e), e.getHeaders());
        }

        List<ImportedSupplyRow> results = new ArrayList<>();
        for (CsvImportRow row : file.rows()) {
            results.add(importRow(communityId, row));
        }
        ImportedSupplies imported = new ImportedSupplies(results);

        LOGGER.info("Supplies imported: communityId={}, created={}, failed={}, encoding={}", communityId,
                imported.created(), imported.failed(), file.encoding());
        return imported;
    }

    private ImportedSupplyRow importRow(UUID communityId, CsvImportRow row) {
        if (row.isMalformed()) {
            return ImportedSupplyRow.of(row.line(), SupplyImportRowOutcome.MALFORMED);
        }
        Map<String, String> values = row.values();

        List<ColumnViolation> violations = violationsOf(values);
        if (!violations.isEmpty()) {
            return new ImportedSupplyRow(row.line(), SupplyImportRowOutcome.INVALID, violations);
        }

        Supply supply = new Supply.Builder()
                .withCode(values.get(CODE))
                .withAddress(values.get(ADDRESS))
                .withAddressRef(emptyToNull(values.get(ADDRESS_REF)))
                .withName(emptyToNull(values.get(NAME)))
                .build();
        try {
            createSupplyService.create(supply, UserPersonalId.of(values.get(PERSONAL_ID)), communityId);
            return ImportedSupplyRow.of(row.line(), SupplyImportRowOutcome.CREATED);
        } catch (SupplyOwnerNotFoundException e) {
            return ImportedSupplyRow.of(row.line(), SupplyImportRowOutcome.OWNER_NOT_FOUND);
        } catch (SupplyAlreadyExistsException e) {
            return ImportedSupplyRow.of(row.line(), SupplyImportRowOutcome.ALREADY_EXISTS);
        } catch (SupplyRegisteredInAnotherCommunityException e) {
            return ImportedSupplyRow.of(row.line(), SupplyImportRowOutcome.REGISTERED_IN_ANOTHER_COMMUNITY);
        }
    }

    /**
     * One per broken rule, in the order of the columns and then of the rules, so the same row always reads the same
     * way.
     */
    private static List<ColumnViolation> violationsOf(Map<String, String> values) {
        List<ColumnViolation> violations = new ArrayList<>();
        for (String column : REQUIRED_COLUMNS) {
            if (isBlank(values.get(column))) {
                violations.add(ColumnViolation.of(column, SupplyImportConstraint.REQUIRED));
            }
        }
        String code = values.get(CODE);
        if (!isBlank(code) && SupplyCode.ofCups(code).isEmpty()) {
            violations.add(ColumnViolation.of(CODE, SupplyImportConstraint.INVALID_CUPS));
        }
        return violations.stream()
                .sorted(Comparator.comparingInt((ColumnViolation violation) -> COLUMNS.indexOf(violation.column()))
                        .thenComparing(ColumnViolation::constraint))
                .toList();
    }

    private static SupplyImportFileProblem problemOf(CsvImportFileRefusedException e) {
        return switch (e.getProblem()) {
            case INVALID_ENCODING -> SupplyImportFileProblem.INVALID_ENCODING;
            case AMBIGUOUS_SEPARATOR -> SupplyImportFileProblem.AMBIGUOUS_SEPARATOR;
            case MALFORMED_FILE -> SupplyImportFileProblem.MALFORMED_FILE;
            case UNKNOWN_HEADERS -> SupplyImportFileProblem.UNKNOWN_HEADERS;
            case DUPLICATED_HEADERS -> SupplyImportFileProblem.DUPLICATED_HEADERS;
            case MISSING_HEADERS -> SupplyImportFileProblem.MISSING_HEADERS;
            case TOO_MANY_ROWS -> SupplyImportFileProblem.TOO_MANY_ROWS;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * An optional column left out of the file, or an empty or blank cell, is stored as null.
     */
    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }
}
