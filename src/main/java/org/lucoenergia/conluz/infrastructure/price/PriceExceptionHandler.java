package org.lucoenergia.conluz.infrastructure.price;

import org.lucoenergia.conluz.domain.price.sync.InvalidPriceSyncPeriodException;
import org.lucoenergia.conluz.domain.price.sync.PriceSyncPeriodValidator;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class PriceExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public PriceExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(InvalidPriceSyncPeriodException.class)
    public ResponseEntity<RestError> handleException(InvalidPriceSyncPeriodException e) {

        String messageKey = switch (e.getReason()) {
            case START_AFTER_END -> "error.price.sync.period.start.after.end";
            case TOO_LONG -> "error.price.sync.period.too.long";
            case END_BEYOND_TOMORROW -> "error.price.sync.period.end.beyond.tomorrow";
        };
        RestErrorCode code = switch (e.getReason()) {
            case START_AFTER_END -> RestErrorCode.PRICE_SYNC_PERIOD_START_AFTER_END;
            case TOO_LONG -> RestErrorCode.PRICE_SYNC_PERIOD_TOO_LONG;
            case END_BEYOND_TOMORROW -> RestErrorCode.PRICE_SYNC_PERIOD_END_BEYOND_TOMORROW;
        };

        String message = messageSource.getMessage(
                messageKey,
                List.of("startDate", "endDate", PriceSyncPeriodValidator.MAX_DAYS).toArray(),
                LocaleContextHolder.getLocale()
        );
        // No params: the caller supplied every value the message refers to.
        return errorBuilder.build(message, code, null, HttpStatus.BAD_REQUEST);
    }
}
