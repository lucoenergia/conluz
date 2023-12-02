package org.lucoenergia.conluz.infrastructure.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.apache.commons.collections4.map.HashedMap;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public class JwtAuthHeaderGenerator {

    public static String generate() {
        return generate(MockUser.USERNAME);
    }

    public static String generate(String username) {
        Instant now = Instant.now();

        String token = Jwts.builder()
                .addClaims(new HashedMap<>())
                .setSubject(username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(getExpirationDuration())))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();

        return "Bearer " + token;
    }

    private static Key getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(JwtSecretKeyGenerator.generate());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static Duration getExpirationDuration() {
        return Duration.ofMinutes(1);
    }
}
