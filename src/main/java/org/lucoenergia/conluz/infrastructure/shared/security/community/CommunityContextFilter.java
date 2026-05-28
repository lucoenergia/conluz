package org.lucoenergia.conluz.infrastructure.shared.security.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class CommunityContextFilter extends OncePerRequestFilter {

    public static final String COMMUNITY_ID_HEADER = "X-Community-Id";

    private final CommunityContext communityContext;
    private final ObjectMapper objectMapper;
    private final ErrorBuilder errorBuilder;

    public CommunityContextFilter(CommunityContext communityContext,
                                  ObjectMapper objectMapper,
                                  ErrorBuilder errorBuilder) {
        this.communityContext = communityContext;
        this.objectMapper = objectMapper;
        this.errorBuilder = errorBuilder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User)) {
            // Not authenticated — skip community context resolution
            filterChain.doFilter(request, response);
            return;
        }

        User user = (User) authentication.getPrincipal();
        String communityIdHeader = request.getHeader(COMMUNITY_ID_HEADER);

        if (communityIdHeader != null && !communityIdHeader.isBlank()) {
            // Parse the provided community id
            UUID communityId;
            try {
                communityId = UUID.fromString(communityIdHeader.trim());
            } catch (IllegalArgumentException e) {
                writeBadRequestResponse(response, "Invalid X-Community-Id header value: not a valid UUID");
                return;
            }

            // Platform admin or ADMIN role can use any community without membership check
            if (user.getRole() == Role.ADMIN || Boolean.TRUE.equals(user.isPlatformAdmin())) {
                communityContext.setActiveCommunityId(communityId);
                filterChain.doFilter(request, response);
                return;
            }

            // Check membership
            boolean isMember = user.getMemberships() != null && user.getMemberships().stream()
                    .anyMatch(m -> communityId.equals(m.getCommunity().getId())
                            && Boolean.TRUE.equals(m.isEnabled()));

            if (!isMember) {
                writeForbiddenResponse(response);
                return;
            }

            communityContext.setActiveCommunityId(communityId);
        } else {
            // No header — auto-detect from memberships
            if (user.getMemberships() != null) {
                List<CommunityMembership> activeMemberships = user.getMemberships().stream()
                        .filter(m -> Boolean.TRUE.equals(m.isEnabled()))
                        .toList();

                if (activeMemberships.size() == 1) {
                    communityContext.setActiveCommunityId(activeMemberships.get(0).getCommunity().getId());
                }
                // If 0 or more than 1 active memberships, leave unset
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeBadRequestResponse(HttpServletResponse response, String message) throws IOException {
        ResponseEntity<RestError> entity = errorBuilder.build(message, HttpStatus.BAD_REQUEST);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), entity.getBody());
    }

    private void writeForbiddenResponse(HttpServletResponse response) throws IOException {
        ResponseEntity<RestError> entity = errorBuilder.build(
                "Access denied: user is not a member of the requested community",
                HttpStatus.FORBIDDEN);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), entity.getBody());
    }
}
