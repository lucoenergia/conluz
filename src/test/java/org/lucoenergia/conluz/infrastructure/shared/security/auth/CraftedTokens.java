package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds tokens the way a client or an attacker could, independently of the code under test: signed with the
 * application's key, with another key, with another algorithm, unsigned, tampered or malformed.
 */
public class CraftedTokens {

    private static final Duration LIFETIME = Duration.ofHours(1);

    private final Key key;

    public CraftedTokens(String base64SecretKey) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64SecretKey));
    }

    /**
     * A token the application would accept for that user: signed with its key, with every claim it issues.
     */
    public String valid(UUID userId) {
        return issuedAt(userId, Instant.now());
    }

    /**
     * Signed with the application's key, and otherwise valid, but issued at {@code issuedAt}.
     */
    public String issuedAt(UUID userId, Instant issuedAt) {
        return signed(builder -> withClaims(builder, userId, issuedAt, Instant.now().plus(LIFETIME)));
    }

    /**
     * Signed with the application's key, with an expiry in the past.
     */
    public String expired(UUID userId) {
        Instant issuedAt = Instant.now().minus(LIFETIME.multipliedBy(2));
        return signed(builder -> withClaims(builder, userId, issuedAt, issuedAt.plus(LIFETIME)));
    }

    /**
     * Unsigned ({@code alg: none}) and expired: jjwt checks the expiry of unsigned tokens too.
     */
    public String expiredUnsigned(UUID userId) {
        Instant issuedAt = Instant.now().minus(LIFETIME.multipliedBy(2));
        JwtBuilder builder = Jwts.builder();
        withClaims(builder, userId, issuedAt, issuedAt.plus(LIFETIME));
        return builder.compact();
    }

    /**
     * Signed with the application's key, with the claims set by {@code claims} only.
     */
    public String signed(Consumer<JwtBuilder> claims) {
        JwtBuilder builder = Jwts.builder();
        claims.accept(builder);
        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    /**
     * A valid token whose payload was changed after signing: another {@code jti}, the original signature kept.
     */
    public String tampered(UUID userId) {
        String[] segments = valid(userId).split("\\.");
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode payload = (ObjectNode) mapper.readTree(Base64.getUrlDecoder().decode(segments[1]));
            payload.put("jti", UUID.randomUUID().toString());
            return segments[0] + "." + encode(mapper.writeValueAsString(payload)) + "." + segments[2];
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String signedWithOtherKey(UUID userId) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plus(LIFETIME)))
                .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * {@code alg: none}, with no signature segment.
     */
    public String unsigned(UUID userId) {
        JwtBuilder builder = Jwts.builder();
        withClaims(builder, userId, Instant.now(), Instant.now().plus(LIFETIME));
        return builder.compact();
    }

    /**
     * RS256, an algorithm the application's HMAC key cannot verify.
     */
    public String signedWithRsa(UUID userId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            JwtBuilder builder = Jwts.builder();
            withClaims(builder, userId, Instant.now(), Instant.now().plus(LIFETIME));
            return builder.signWith(generator.generateKeyPair().getPrivate(), SignatureAlgorithm.RS256).compact();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * HS512, which needs a longer key than the application's.
     */
    public String signedWithHs512(UUID userId) {
        JwtBuilder builder = Jwts.builder();
        withClaims(builder, userId, Instant.now(), Instant.now().plus(LIFETIME));
        return builder.signWith(Keys.secretKeyFor(SignatureAlgorithm.HS512), SignatureAlgorithm.HS512).compact();
    }

    /**
     * A header naming an algorithm that does not exist, with a valid payload and a made-up signature.
     */
    public String unknownAlgorithm(UUID userId) {
        String[] segments = valid(userId).split("\\.");
        return encode("{\"alg\":\"FOO\"}") + "." + segments[1] + "." + segments[2];
    }

    /**
     * A header that is valid base64 but not JSON.
     */
    public String headerNotJson(UUID userId) {
        String[] segments = valid(userId).split("\\.");
        return encode("this is not json") + "." + segments[1] + "." + segments[2];
    }

    private static void withClaims(JwtBuilder builder, UUID userId, Instant issuedAt, Instant expiration) {
        builder.setSubject(userId.toString())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiration));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
