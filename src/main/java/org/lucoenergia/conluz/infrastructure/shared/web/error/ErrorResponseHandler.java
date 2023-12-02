package org.lucoenergia.conluz.infrastructure.shared.web.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ErrorResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorResponseHandler.class);

    protected ResponseEntity<Object> handleError(Exception ex) {
        log.error("Unexpected error", ex);
        // Customize your exception handling logic here
        return new ResponseEntity<>("An error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
