package org.lucoenergia.conluz.infrastructure.shared.io;

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

    public CsvFileRequestValidator(MessageSource messageSource) {
        this.messageSource = messageSource;
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
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<RestError> buildUnsupportedExtensionErrorResponse() {
        String message = messageSource.getMessage(
                "error.http.extension.not.supported",
                new List[]{},
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
