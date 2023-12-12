package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
import java.util.function.Function;

@Repository
public class JwtAuthRepository implements AuthRepository {

    private final JwtConfiguration jwtConfiguration;

    public JwtAuthRepository(JwtConfiguration jwtConfiguration) {
        this.jwtConfiguration = jwtConfiguration;
    }

    @Override
    public Token getToken(User user) {

        Instant now = Instant.now();

        String token = Jwts.builder()
                .addClaims(new HashedMap<>())
                .setSubject(user.getUsername())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(getExpirationDuration())))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();

        return Token.of(token);
    }

    @Override
    public String getUsernameFromToken(Token token) {
        return getClaim(token, Claims::getSubject);
    }

    @Override
    public boolean isTokenValid(Token token, User user) {
        final String username = getUsernameFromToken(token);
        return username.equals(user.getUsername()) && !isTokenExpired(token);
    }

    private Date getExpirationDate(Token token) {
        return getClaim(token, Claims::getExpiration);
    }

    private Duration getExpirationDuration() {
        return Duration.ofMinutes(jwtConfiguration.getExpirationTime());
    }

    private boolean isTokenExpired(Token token) {
        return getExpirationDate(token).before(new Date());
    }

    private Key getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfiguration.getSecretKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Claims getAllClaims(Token token) {
        return Jwts
                .parserBuilder().setSigningKey(getKey()).build()
                .parseClaimsJws(token.getToken()).getBody();
    }

    private <T> T getClaim(Token token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaims(token);
        return claimsResolver.apply(claims);
    }
}
