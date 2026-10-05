package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.security.WeakKeyException;
import org.apache.commons.collections4.map.HashedMap;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.auth.TokenRejectionReason;
import org.lucoenergia.conluz.domain.admin.user.auth.VerifiedToken;
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
    public VerifiedToken verify(Token token) {
        Claims claims = getAllClaims(token);
        if (claims.getSubject() == null || claims.getId() == null || claims.getExpiration() == null) {
            throw new InvalidTokenException(TokenRejectionReason.MISSING_CLAIMS);
        }
        return new VerifiedToken(subjectOf(claims), claims.getId());
    }

    @Override
    public Optional<TokenRejectionReason> findRejectionReason(Token token, User user) {
        Claims claims = getAllClaims(token);
        if (claims.getSubject() == null || claims.getExpiration() == null) {
            return Optional.of(TokenRejectionReason.MISSING_CLAIMS);
        }
        if (!subjectOf(claims).equals(user.getId())) {
            return Optional.of(TokenRejectionReason.SUBJECT_MISMATCH);
        }
        if (!user.isEnabled()) {
            return Optional.of(TokenRejectionReason.USER_DISABLED);
        }
        if (claims.getExpiration().before(new Date())) {
            return Optional.of(TokenRejectionReason.EXPIRED);
        }
        if (isIssuedBefore(claims, user.getPasswordChangedAt())) {
            return Optional.of(TokenRejectionReason.ISSUED_BEFORE_PASSWORD_CHANGE);
        }
        if (isIssuedBefore(claims, user.getDisabledAt())) {
            return Optional.of(TokenRejectionReason.ISSUED_BEFORE_DISABLE);
        }
        return Optional.empty();
    }

    @Override
    public Date getExpirationDate(Token token) {
        return getClaim(token, Claims::getExpiration);
    }

    private Duration getExpirationDuration() {
        return Duration.ofMinutes(jwtConfiguration.getExpirationTime());
    }

    private static UUID subjectOf(Claims claims) {
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException(TokenRejectionReason.INVALID_CLAIMS);
        }
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
    private static boolean isIssuedBefore(Claims claims, Instant cutoff) {
        if (cutoff == null) {
            return false;
        }
        Date issuedAt = claims.getIssuedAt();
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

    /**
     * The token's claims, once its signature, format and expiry are verified. Any failure to verify or parse it is
     * rejected as an {@link InvalidTokenException}, never with jjwt's exception, whose message may describe the token.
     * A missing or invalid key is a configuration error, not a rejected token, so it is left to propagate.
     */
    private Claims getAllClaims(Token token) {
        Key key = getKey();
        try {
            return Jwts
                    .parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token.getToken()).getBody();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException(reasonFor(e), verifiedSubjectOf(e));
        }
    }

    /**
     * Classifies every jjwt failure, so that a subtype not listed here still rejects the token instead of failing
     * the request. jjwt reports a key that does not fit the token's algorithm, such as HS512 against the
     * application's key, as a {@code KeyException}, which is unsupported. It reports an algorithm name it does not
     * know with the same exception as a signature that does not match, so that case is an invalid signature.
     */
    private static TokenRejectionReason reasonFor(RuntimeException e) {
        if (e instanceof io.jsonwebtoken.security.SignatureException) {
            return TokenRejectionReason.INVALID_SIGNATURE;
        }
        if (e instanceof ExpiredJwtException expired) {
            // jjwt also checks the expiry of an unsigned token, which would be rejected as unsupported anyway
            return expired.getHeader() instanceof JwsHeader
                    ? TokenRejectionReason.EXPIRED
                    : TokenRejectionReason.UNSUPPORTED;
        }
        if (e instanceof UnsupportedJwtException || e instanceof SecurityException) {
            return TokenRejectionReason.UNSUPPORTED;
        }
        if (e instanceof ClaimJwtException) {
            return TokenRejectionReason.INVALID_CLAIMS;
        }
        return TokenRejectionReason.MALFORMED;
    }

    /**
     * The subject of a token rejected only for its expiry. jjwt verifies the signature of a signed token before its
     * expiry, so the subject can be trusted when the header is a {@link JwsHeader}. In every other case, the claims
     * were never verified, and their subject is not reported.
     */
    private static UUID verifiedSubjectOf(RuntimeException e) {
        if (e instanceof ExpiredJwtException expired && expired.getHeader() instanceof JwsHeader
                && expired.getClaims() != null && expired.getClaims().getSubject() != null) {
            try {
                return UUID.fromString(expired.getClaims().getSubject());
            } catch (IllegalArgumentException notAUserId) {
                return null;
            }
        }
        return null;
    }

    private <T> T getClaim(Token token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaims(token);
        return claimsResolver.apply(claims);
    }
}
