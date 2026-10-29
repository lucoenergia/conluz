package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyCodeInvalidException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyRegisteredInAnotherCommunityException;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportSuppliesService;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportFileRefusedException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.delete.SupplyHasPartitionCoefficientsException;
import org.lucoenergia.conluz.domain.admin.supply.delete.SupplyHasPlantsException;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class SupplyExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public SupplyExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(SupplyNotFoundException.class)
    public ResponseEntity<RestError> handleException(SupplyNotFoundException e) {

        String identifier;
        if (e.getId() != null) {
            // String.valueOf: SupplyId.of(null) is legal, and a dereference here would turn this
            // 404 into a 500.
            identifier = String.valueOf(e.getId().getId());
        } else {
            identifier = e.getCode().getCode();
        }

        String message = messageSource.getMessage(
                "error.supply.not.found",
                Collections.singletonList(identifier).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }

    /**
     * A 409: a plant is attached to the supply, so it is not deleted (SUP-001).
     */
    @ExceptionHandler(SupplyHasPlantsException.class)
    public ResponseEntity<RestError> handleException(SupplyHasPlantsException e) {
        String supplyId = e.getSupplyId().getId().toString();

        String message = messageSource.getMessage(
                "error.supply.has.plants",
                List.of(supplyId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.SUPPLY_HAS_PLANTS, Map.of("supplyId", supplyId),
                HttpStatus.CONFLICT);
    }

    /**
     * A 409: a sharing agreement holds partition coefficients for the supply, so it is not deleted (SUP-001).
     */
    @ExceptionHandler(SupplyHasPartitionCoefficientsException.class)
    public ResponseEntity<RestError> handleException(SupplyHasPartitionCoefficientsException e) {
        String supplyId = e.getSupplyId().getId().toString();

        String message = messageSource.getMessage(
                "error.supply.has.partition.coefficients",
                List.of(supplyId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.SUPPLY_HAS_PARTITION_COEFFICIENTS,
                Map.of("supplyId", supplyId), HttpStatus.CONFLICT);
    }

    /**
     * A 400: another supply of the same community has the same CUPS, in either length and in any case (SUP-005).
     */
    @ExceptionHandler(SupplyAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(SupplyAlreadyExistsException e) {
        return refused("error.supply.already.exists", RestErrorCode.SUPPLY_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
    }

    /**
     * A 400: a supply of another community has the same CUPS (SUP-005). The message names neither that community
     * nor its owner.
     */
    @ExceptionHandler(SupplyRegisteredInAnotherCommunityException.class)
    public ResponseEntity<RestError> handleException(SupplyRegisteredInAnotherCommunityException e) {
        return refused("error.supply.registered.in.another.community",
                RestErrorCode.SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY, HttpStatus.BAD_REQUEST);
    }

    /**
     * A 400: the code written to a supply is not a CUPS (SUP-004).
     */
    @ExceptionHandler(SupplyCodeInvalidException.class)
    public ResponseEntity<RestError> handleException(SupplyCodeInvalidException e) {
        return refused("error.supply.cups.invalid", RestErrorCode.SUPPLY_CUPS_INVALID, HttpStatus.BAD_REQUEST);
    }

    /**
     * A 404: the owner of a supply being created is unknown, or not a member of its community; the two are answered
     * alike (SUP-002).
     */
    @ExceptionHandler(SupplyOwnerNotFoundException.class)
    public ResponseEntity<RestError> handleException(SupplyOwnerNotFoundException e) {
        return refused("error.supply.owner.not.found", RestErrorCode.SUPPLY_OWNER_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    /**
     * A 400: the file of supplies is refused as a whole, and nothing was created (SUP-003). Not logged with the
     * headers: a file without a header line holds a supply's data in its first line, and the service has already
     * logged the refusal without them.
     */
    @ExceptionHandler(SupplyImportFileRefusedException.class)
    public ResponseEntity<RestError> handleException(SupplyImportFileRefusedException e) {
        // Quoted in the message, so that a blank header can be seen; comma-separated in the params for the client
        List<String> named = List.of(String.join(", ", e.getHeaders().stream().map(h -> "'" + h + "'").toList()));
        Map<String, String> headers = Map.of("headers", String.join(",", e.getHeaders()));
        return switch (e.getProblem()) {
            case INVALID_ENCODING -> importRefused("error.supply.import.invalid.encoding", List.of(),
                    RestErrorCode.SUPPLY_IMPORT_INVALID_ENCODING, Map.of());
            case AMBIGUOUS_SEPARATOR -> importRefused("error.supply.import.ambiguous.separator", List.of(),
                    RestErrorCode.SUPPLY_IMPORT_AMBIGUOUS_SEPARATOR, Map.of());
            case MALFORMED_FILE -> importRefused("error.supply.import.malformed.file", List.of(),
                    RestErrorCode.SUPPLY_IMPORT_MALFORMED_FILE, Map.of());
            case UNKNOWN_HEADERS -> importRefused("error.supply.import.unknown.headers", named,
                    RestErrorCode.SUPPLY_IMPORT_UNKNOWN_HEADERS, headers);
            case DUPLICATED_HEADERS -> importRefused("error.supply.import.duplicated.headers", named,
                    RestErrorCode.SUPPLY_IMPORT_DUPLICATED_HEADERS, headers);
            case MISSING_HEADERS -> importRefused("error.supply.import.missing.headers", named,
                    RestErrorCode.SUPPLY_IMPORT_MISSING_HEADERS, headers);
            case TOO_MANY_ROWS -> {
                String maxRows = String.valueOf(ImportSuppliesService.MAX_ROWS);
                yield importRefused("error.supply.import.too.many.rows", List.of(maxRows),
                        RestErrorCode.SUPPLY_IMPORT_TOO_MANY_ROWS, Map.of("maxRows", maxRows));
            }
        };
    }

    private ResponseEntity<RestError> importRefused(String messageKey, List<String> args, RestErrorCode code,
                                                    Map<String, String> params) {
        String message = messageSource.getMessage(messageKey, args.toArray(), LocaleContextHolder.getLocale());
        return errorBuilder.buildWithoutLogging(message, code, params, HttpStatus.BAD_REQUEST);
    }

    /**
     * The message is written from its key alone, so that it never holds a value of the request.
     */
    private ResponseEntity<RestError> refused(String messageKey, RestErrorCode code, HttpStatus status) {
        String message = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        return errorBuilder.build(message, code, Map.of(), status);
    }

    @ExceptionHandler(InvalidEnergyMetricsPeriodException.class)
    public ResponseEntity<RestError> handleException(InvalidEnergyMetricsPeriodException e) {

        String messageKey = switch (e.getReason()) {
            case START_AFTER_END -> "error.energy.metrics.period.start.after.end";
            case START_IN_FUTURE -> "error.energy.metrics.period.start.in.future";
            case TOO_LONG -> "error.energy.metrics.period.too.long";
            case INCOMPLETE_PERIOD -> "error.energy.metrics.period.incomplete";
            case CONFLICTING_PERIOD -> "error.energy.metrics.period.conflicting";
        };
        RestErrorCode code = switch (e.getReason()) {
            case START_AFTER_END -> RestErrorCode.ENERGY_METRICS_PERIOD_START_AFTER_END;
            case START_IN_FUTURE -> RestErrorCode.ENERGY_METRICS_PERIOD_START_IN_FUTURE;
            case TOO_LONG -> RestErrorCode.ENERGY_METRICS_PERIOD_TOO_LONG;
            case INCOMPLETE_PERIOD -> RestErrorCode.ENERGY_METRICS_PERIOD_INCOMPLETE;
            case CONFLICTING_PERIOD -> RestErrorCode.ENERGY_METRICS_PERIOD_CONFLICTING;
        };

        String message = messageSource.getMessage(
                messageKey,
                List.of("startDate", "endDate", "period", EnergyMetricsPeriodValidator.MAX_MONTHS).toArray(),
                LocaleContextHolder.getLocale()
        );
        // No params: the caller supplied every value the message refers to.
        return errorBuilder.build(message, code, null, HttpStatus.BAD_REQUEST);
    }
}
