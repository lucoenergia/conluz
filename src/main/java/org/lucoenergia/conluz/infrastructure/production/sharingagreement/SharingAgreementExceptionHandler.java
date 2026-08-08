package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import org.lucoenergia.conluz.domain.production.sharingagreement.*;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileValidationException;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationException;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFileNotFoundException;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.activation.CoefficientActivationErrorMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.distributorfile.DistributorFileErrorMapper;
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
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class SharingAgreementExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;
    private final DistributorFileErrorMapper distributorFileErrorMapper;
    private final CoefficientActivationErrorMapper coefficientActivationErrorMapper;

    public SharingAgreementExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder,
                                            DistributorFileErrorMapper distributorFileErrorMapper,
                                            CoefficientActivationErrorMapper coefficientActivationErrorMapper) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
        this.distributorFileErrorMapper = distributorFileErrorMapper;
        this.coefficientActivationErrorMapper = coefficientActivationErrorMapper;
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

    @ExceptionHandler(SharingAgreementNotRevertibleException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementNotRevertibleException e) {

        String message = messageSource.getMessage(
                "error.sharing.agreement.not.revertible",
                new Object[]{e.getId(), e.getCurrentStatus()},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.SHARING_AGREEMENT_NOT_REVERTIBLE, null, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SharingAgreementHasAppliedCoefficientsException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementHasAppliedCoefficientsException e) {

        String message = messageSource.getMessage(
                "error.sharing.agreement.has.applied.coefficients",
                Collections.singletonList(e.getId()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.SHARING_AGREEMENT_HAS_APPLIED_COEFFICIENTS, null, HttpStatus.CONFLICT);
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

    @ExceptionHandler(SharingAgreementCoefficientSumInvalidException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementCoefficientSumInvalidException e) {

        String message = messageSource.getMessage(
                "error.sharing.agreement.coefficient.sum.invalid",
                new Object[]{e.getId(), e.getActualSum().toPlainString()},
                LocaleContextHolder.getLocale()
        );
        Map<String, String> params = Map.of("actualSum", e.getActualSum().toPlainString());
        return errorBuilder.build(message, RestErrorCode.SHARING_AGREEMENT_COEFFICIENT_SUM_INVALID, params, HttpStatus.CONFLICT);
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
