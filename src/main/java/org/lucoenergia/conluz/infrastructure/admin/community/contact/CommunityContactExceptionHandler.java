package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelsInvalidException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorDetail;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class CommunityContactExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;
    private final ContactChannelViolationMapper violationMapper;

    public CommunityContactExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder,
                                            ContactChannelViolationMapper violationMapper) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
        this.violationMapper = violationMapper;
    }

    /**
     * One error per broken rule. With a single one, the summary is that error's message, as {@link RestError}
     * requires.
     */
    @ExceptionHandler(ContactChannelsInvalidException.class)
    public ResponseEntity<RestError> handleException(ContactChannelsInvalidException e) {
        List<RestErrorDetail> details = e.getViolations().stream()
                .map(violation -> violationMapper.toRestErrorDetail(violation, LocaleContextHolder.getLocale()))
                .toList();
        String summary = details.size() == 1
                ? details.get(0).getMessage()
                : messageSource.getMessage("error.contact.channels.invalid", new Object[]{details.size()},
                LocaleContextHolder.getLocale());
        return errorBuilder.build(summary, details, HttpStatus.BAD_REQUEST);
    }
}
