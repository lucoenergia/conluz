package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.plant.PlantAlreadyExistsException;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileValidationException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.DuplicatePartitionCoefficientEntryException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementHasNoCoefficientsException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotDraftException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotPublishedException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementFileNotFoundException;
import org.lucoenergia.conluz.infrastructure.production.plant.distributorfile.DistributorFileErrorMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.activation.CoefficientActivationErrorMapper;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorDetail;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class PlantExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;
    private final DistributorFileErrorMapper distributorFileErrorMapper;
    private final CoefficientActivationErrorMapper coefficientActivationErrorMapper;

    public PlantExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder,
                                  DistributorFileErrorMapper distributorFileErrorMapper,
                                  CoefficientActivationErrorMapper coefficientActivationErrorMapper) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
        this.distributorFileErrorMapper = distributorFileErrorMapper;
        this.coefficientActivationErrorMapper = coefficientActivationErrorMapper;
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

        String plantIdentifier = e.getId() != null ? e.getId().toString() : e.getProviderCode();

        String message = messageSource.getMessage(
                "error.plant.not.found",
                Collections.singletonList(plantIdentifier).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SharingAgreementNotFoundException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementNotFoundException e) {

        String message = messageSource.getMessage(
                "error.sharing.agreement.not.found",
                Collections.singletonList(e.getId()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SharingAgreementFileNotFoundException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementFileNotFoundException e) {

        String message = messageSource.getMessage(
                "error.sharing.agreement.file.not.found",
                Collections.singletonList(e.getId()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SharingAgreementNotDraftException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementNotDraftException e) {

        String message = messageSource.getMessage(
                "error.sharing.agreement.not.draft",
                new Object[]{e.getId(), e.getCurrentStatus()},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.SHARING_AGREEMENT_NOT_DRAFT, null, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SharingAgreementHasNoCoefficientsException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementHasNoCoefficientsException e) {

        String message = messageSource.getMessage(
                "error.sharing.agreement.no.coefficients",
                Collections.singletonList(e.getId()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.SHARING_AGREEMENT_HAS_NO_COEFFICIENTS, null, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DuplicatePartitionCoefficientEntryException.class)
    public ResponseEntity<RestError> handleException(DuplicatePartitionCoefficientEntryException e) {

        String message = messageSource.getMessage(
                "error.sharing.agreement.duplicate.cups",
                new Object[]{e.getSharingAgreementId(), e.getCups()},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.SHARING_AGREEMENT_DUPLICATE_CUPS, null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SharingAgreementNotPublishedException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementNotPublishedException e) {

        String message = messageSource.getMessage(
                "error.sharing.agreement.not.published",
                new Object[]{e.getId(), e.getCurrentStatus()},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.SHARING_AGREEMENT_NOT_PUBLISHED, null, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CoefficientActivationException.class)
    public ResponseEntity<RestError> handleException(CoefficientActivationException e) {

        List<RestErrorDetail> details = e.getErrors().stream()
                .map(error -> coefficientActivationErrorMapper.toRestErrorDetail(error, LocaleContextHolder.getLocale()))
                .collect(Collectors.toList());

        String summary = messageSource.getMessage(
                "error.sharing.agreement.coefficient.activation.invalid",
                new Object[]{e.getErrors().size()},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(summary, details, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DistributorFileValidationException.class)
    public ResponseEntity<RestError> handleException(DistributorFileValidationException e) {

        List<RestErrorDetail> details = e.getErrors().stream()
                .map(error -> distributorFileErrorMapper.toRestErrorDetail(error, LocaleContextHolder.getLocale()))
                .collect(Collectors.toList());

        String summary = messageSource.getMessage(
                "error.distributor.file.invalid",
                new Object[]{e.getErrors().size()},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(summary, details, HttpStatus.BAD_REQUEST);
    }
}
