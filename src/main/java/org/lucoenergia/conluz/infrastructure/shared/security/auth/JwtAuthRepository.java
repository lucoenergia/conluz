package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.apache.commons.collections4.map.HashedMap;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

@Repository
public class JwtAuthRepository implements AuthRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthRepository.class);

    private static final String CUSTOM_CLAIM_IS_PLATFORM_ADMIN = "is_platform_admin";
    private static final String CUSTOM_CLAIM_COMMUNITY_MEMBERSHIPS = "community_memberships";

    private final JwtConfiguration jwtConfiguration;

    public JwtAuthRepository(JwtConfiguration jwtConfiguration) {
        this.jwtConfiguration = jwtConfiguration;
    }

    @Override
    public Token getToken(User user) {

        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .addClaims(getCustomClaims(user))
                .setSubject(user.getId().toString())
                .setId(jti)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(getExpirationDuration())))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();

        return Token.of(token);
    }

    private Map<String, Object> getCustomClaims(User user) {
        Map<String, Object> claims = new HashedMap<>();
        claims.put(CUSTOM_CLAIM_IS_PLATFORM_ADMIN, user.isPlatformAdmin());
        Map<String, String> membershipMap = new HashMap<>();
        if (user.getMemberships() != null) {
            for (CommunityMembership m : user.getMemberships()) {
                membershipMap.put(m.getCommunity().getId().toString(), m.getRole().name());
            }
        }
        claims.put(CUSTOM_CLAIM_COMMUNITY_MEMBERSHIPS, membershipMap);
        return claims;
    }

    @Override
    public boolean isPlatformAdmin(Token token) {
        Object claim = getAllClaims(token).get(CUSTOM_CLAIM_IS_PLATFORM_ADMIN);
        return Boolean.TRUE.equals(claim);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getCommunityMemberships(Token token) {
        Object claim = getAllClaims(token).get(CUSTOM_CLAIM_COMMUNITY_MEMBERSHIPS);
        if (claim instanceof Map) {
            return (Map<String, String>) claim;
        }
        return Map.of();
    }

    @Override
    public Optional<String> getJtiFromToken(Token token) {
        try {
            return Optional.ofNullable(getClaim(token, Claims::getId));
        } catch (Exception e) {
            LOGGER.error("Error extracting JTI from token", e);
            return Optional.empty();
        }
    }

    @Override
    public UUID getUserIdFromToken(Token token) {
        try {
            return UUID.fromString(getClaim(token, Claims::getSubject));
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException(token.getToken());
        }
    }

    @Override
    public boolean isTokenValid(Token token, User user) {
        final UUID id = getUserIdFromToken(token);
        return id.equals(user.getId())
                && user.isEnabled()
                && !isTokenExpired(token)
                && !isIssuedBefore(token, user.getPasswordChangedAt())
                && !isIssuedBefore(token, user.getDisabledAt());
    }

    @Override
    public Date getExpirationDate(Token token) {
        return getClaim(token, Claims::getExpiration);
    }

    private Duration getExpirationDuration() {
        return Duration.ofMinutes(jwtConfiguration.getExpirationTime());
    }

    private boolean isTokenExpired(Token token) {
        return getExpirationDate(token).before(new Date());
    }

    /**
     * Whether the token was issued before {@code cutoff}: the user's last password change, which ends every
     * session opened with the old password, or their last disable, which ends every session opened before it even
     * after the user is enabled again. A {@code null} cutoff places no restriction.
     * <p>
     * {@code iat} only has second precision, so the cutoff is truncated to the second before comparing: a token
     * issued right after it, within the same second, must be accepted. The cost is that another token issued
     * earlier within that same second is accepted too. For a password change, the token used for the change
     * itself is revoked explicitly through the blacklist instead.
     */
    private boolean isIssuedBefore(Token token, Instant cutoff) {
        if (cutoff == null) {
            return false;
        }
        Date issuedAt = getClaim(token, Claims::getIssuedAt);
        if (issuedAt == null) {
            return true;
        }
        return issuedAt.toInstant().isBefore(cutoff.truncatedTo(ChronoUnit.SECONDS));
    }

    private Key getKey() {
        String secretKey = jwtConfiguration.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new SecretKeyNotFoundException();
        }
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secretKey);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (DecodingException e) {
            throw new InvalidSecretKeyException("Secret key is not valid Base64", e);
        } catch (WeakKeyException e) {
            throw new WeakSecretKeyException("Secret key is too short/weak", e);
        }
    }

    private Claims getAllClaims(Token token) {
        try {
            return Jwts
                    .parserBuilder().setSigningKey(getKey()).build()
                    .parseClaimsJws(token.getToken()).getBody();
        } catch (MalformedJwtException | ExpiredJwtException e) {
            LOGGER.error(e.getMessage());
            throw new InvalidTokenException(token.getToken(), e);
        }
    }

    private <T> T getClaim(Token token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaims(token);
        return claimsResolver.apply(claims);
    }
}
