package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.plant.PlantAlreadyExistsException;
import org.lucoenergia.conluz.domain.production.plant.PlantCilAlreadyInUseException;
import org.lucoenergia.conluz.domain.production.plant.PlantCilInvalidException;
import org.lucoenergia.conluz.domain.production.plant.SupplyAlreadyHasPlantException;
import org.lucoenergia.conluz.domain.production.plant.PlantMissingRegulatoryCodeException;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.delete.PlantHasPartitionCoefficientsException;
import org.lucoenergia.conluz.domain.production.plant.delete.PlantHasSharingAgreementsException;
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
import java.util.Map;

@RestControllerAdvice
public class PlantExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public PlantExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(PlantAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(PlantAlreadyExistsException e) {

        String plantProviderCode = e.getProviderCode().toString();

        String message = messageSource.getMessage(
                "error.plant.already.exists",
                Collections.singletonList(plantProviderCode).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PlantNotFoundException.class)
    public ResponseEntity<RestError> handleException(PlantNotFoundException e) {

        // The bare UUID, as the caller sent it; String.valueOf keeps a PlantId without an id from failing here
        String plantIdentifier = e.getId() != null ? String.valueOf(e.getId().getId()) : e.getProviderCode();

        String message = messageSource.getMessage(
                "error.plant.not.found",
                Collections.singletonList(plantIdentifier).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PlantMissingRegulatoryCodeException.class)
    public ResponseEntity<RestError> handleException(PlantMissingRegulatoryCodeException e) {

        String message = messageSource.getMessage(
                "error.plant.missing.regulatory.code",
                Collections.singletonList(e.getPlantId()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.PLANT_MISSING_REGULATORY_CODE, null, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PlantHasSharingAgreementsException.class)
    public ResponseEntity<RestError> handleException(PlantHasSharingAgreementsException e) {

        String plantId = e.getPlantId().getId().toString();

        String message = messageSource.getMessage(
                "error.plant.has.sharing.agreements",
                Collections.singletonList(plantId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.PLANT_HAS_SHARING_AGREEMENTS, Map.of("plantId", plantId),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PlantHasPartitionCoefficientsException.class)
    public ResponseEntity<RestError> handleException(PlantHasPartitionCoefficientsException e) {

        String plantId = e.getPlantId().getId().toString();

        String message = messageSource.getMessage(
                "error.plant.has.partition.coefficients",
                Collections.singletonList(plantId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.PLANT_HAS_PARTITION_COEFFICIENTS, Map.of("plantId", plantId),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SupplyAlreadyHasPlantException.class)
    public ResponseEntity<RestError> handleException(SupplyAlreadyHasPlantException e) {

        String supplyId = e.getSupplyId().getId().toString();

        String message = messageSource.getMessage(
                "error.supply.already.has.plant",
                Collections.singletonList(supplyId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.SUPPLY_ALREADY_HAS_PLANT, Map.of("supplyId", supplyId),
                HttpStatus.CONFLICT);
    }

    /**
     * A 400: the CIL written to a plant is not one once normalised (PLT-004).
     */
    @ExceptionHandler(PlantCilInvalidException.class)
    public ResponseEntity<RestError> handleException(PlantCilInvalidException e) {
        return refused("error.plant.cil.invalid", RestErrorCode.PLANT_CIL_INVALID, HttpStatus.BAD_REQUEST);
    }

    /**
     * A 409: another plant already holds the CIL (PLT-005).
     */
    @ExceptionHandler(PlantCilAlreadyInUseException.class)
    public ResponseEntity<RestError> handleException(PlantCilAlreadyInUseException e) {
        return refused("error.plant.cil.already.in.use", RestErrorCode.PLANT_CIL_ALREADY_IN_USE,
                HttpStatus.CONFLICT);
    }

    /**
     * The message is written from its key alone, so that it never holds a value of the request.
     */
    private ResponseEntity<RestError> refused(String messageKey, RestErrorCode code, HttpStatus status) {
        String message = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        return errorBuilder.build(message, code, Map.of(), status);
    }
}
