package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.security.PasswordChangeAllowedEndpoints;
import org.lucoenergia.conluz.infrastructure.shared.security.PublicEndpoints;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refuses every request of a user who must change their password (#342) with 403 and the
 * {@code USER_PASSWORD_CHANGE_REQUIRED} code, except the {@link PasswordChangeAllowedEndpoints}.
 * <p>
 * Runs right after the token has authenticated the caller, so it reads the flag from the principal that was just
 * loaded, without a query of its own, and refuses before any community resolution or controller. A refusal is not
 * logged: the client sends a flagged user to the password change, so it is an expected answer, not an incident.
 */
@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final ErrorBuilder errorBuilder;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public PasswordChangeRequiredFilter(ErrorBuilder errorBuilder, MessageSource messageSource,
                                        ObjectMapper objectMapper) {
        this.errorBuilder = errorBuilder;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicEndpoints.MATCHER.matches(request) || PasswordChangeAllowedEndpoints.MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user
                && user.mustChangePassword()) {
            String message = messageSource.getMessage("error.user.password.change.required", null,
                    LocaleContextHolder.getLocale());
            ResponseEntity<RestError> refusal = errorBuilder.buildWithoutLogging(message,
                    RestErrorCode.USER_PASSWORD_CHANGE_REQUIRED, null, HttpStatus.FORBIDDEN);

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), refusal.getBody());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
