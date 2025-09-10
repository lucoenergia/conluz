package org.lucoenergia.conluz.infrastructure.shared.io;

import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class CsvFileRequestValidator {

    public static final String CSV_CONTENT_TYPE = "text/csv";

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public CsvFileRequestValidator(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    public Optional<ResponseEntity<RestError>> validate(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals(CSV_CONTENT_TYPE)) {
            return Optional.of(buildUnsupportedMediaTypeErrorResponse(contentType));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
            return Optional.of(buildUnsupportedExtensionErrorResponse());
        }
        return Optional.empty();
    }

    private ResponseEntity<RestError> buildUnsupportedMediaTypeErrorResponse(String contentType) {
        String message = messageSource.getMessage(
                "error.http.media.type.not.supported",
                Collections.singletonList(contentType).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<RestError> buildUnsupportedExtensionErrorResponse() {
        String message = messageSource.getMessage(
                "error.http.extension.not.supported",
                new List[]{},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }
}
