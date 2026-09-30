package com.hrgenius.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Issues and validates JWT access tokens. Refresh tokens are opaque and stored in the DB
 * (see RefreshToken); this service only deals with the short-lived signed access token.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenTtlMs;
    private final String issuer;

    public JwtService(
            @Value("${hrgenius.security.jwt-secret}") String secret,
            @Value("${hrgenius.security.access-token-ttl-ms}") long accessTokenTtlMs,
            @Value("${hrgenius.security.jwt-issuer:hrgenius}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.issuer = issuer;
    }

    public String generateAccessToken(String subject, Collection<String> authorities) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenTtlMs);
        return Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .claim("authorities", authorities)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String extractSubject(String token) {
        return parse(token, Claims::getSubject);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractAuthorities(String token) {
        return parse(token, claims -> (List<String>) claims.get("authorities", List.class));
    }

    public boolean isValid(String token) {
        try {
            parse(token, Claims::getSubject);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T parse(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
