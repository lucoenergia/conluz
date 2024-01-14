package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.apache.commons.collections4.map.HashedMap;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.springframework.stereotype.Repository;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Repository
public class JwtAuthRepository implements AuthRepository {

    private static final String CUSTOM_CLAIM_ROLE = "role";

    private final JwtConfiguration jwtConfiguration;

    public JwtAuthRepository(JwtConfiguration jwtConfiguration) {
        this.jwtConfiguration = jwtConfiguration;
    }

    @Override
    public Token getToken(User user) {

        Instant now = Instant.now();

        String token = Jwts.builder()
                .addClaims(getCustomClaims(user))
                .setSubject(user.getId().toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(getExpirationDuration())))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();

        return Token.of(token);
    }

    private Map<String, Object> getCustomClaims(User user) {
        Map<String, Object> claims = new HashedMap<>();
        claims.put("role", user.getRole().name());
        return claims;
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
        return id.equals(user.getId()) && !isTokenExpired(token);
    }

    @Override
    public Date getExpirationDate(Token token) {
        return getClaim(token, Claims::getExpiration);
    }

    @Override
    public String getRole(Token token) {
        return (String) getAllClaims(token).get(CUSTOM_CLAIM_ROLE);
    }

    private Duration getExpirationDuration() {
        return Duration.ofMinutes(jwtConfiguration.getExpirationTime());
    }

    private boolean isTokenExpired(Token token) {
        return getExpirationDate(token).before(new Date());
    }

    private Key getKey() {
        String secretKey = jwtConfiguration.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new SecretKeyNotFoundException();
        }
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Claims getAllClaims(Token token) {
        try {
            return Jwts
                    .parserBuilder().setSigningKey(getKey()).build()
                    .parseClaimsJws(token.getToken()).getBody();
        } catch (MalformedJwtException e) {
            throw new InvalidTokenException(token.getToken(), e);
        }
    }

    private <T> T getClaim(Token token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaims(token);
        return claimsResolver.apply(claims);
    }
}
