package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class PartitionCoefficientFileRequestValidator {

    private static final String TXT_CONTENT_TYPE = "text/plain";
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^.+_\\d{4}\\.txt$");

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public PartitionCoefficientFileRequestValidator(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    public Optional<ResponseEntity<RestError>> validate(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals(TXT_CONTENT_TYPE)) {
            return Optional.of(buildUnsupportedMediaTypeErrorResponse(contentType));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".txt")) {
            return Optional.of(buildUnsupportedExtensionErrorResponse());
        }
        if (originalFilename == null || !FILENAME_PATTERN.matcher(originalFilename).matches()) {
            return Optional.of(buildInvalidFilenameErrorResponse(originalFilename));
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
                new Object[]{},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<RestError> buildInvalidFilenameErrorResponse(String filename) {
        String message = messageSource.getMessage(
                "error.http.filename.pattern.not.supported",
                new Object[]{filename},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }
}
