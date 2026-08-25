package com.srs.live.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;

    public JwtUtil(@Value("${srs.jwt.secret}") String secret,
                   @Value("${srs.jwt.expiration:7200}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(String uid, String username, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claim("uid", uid)
                .claim("username", username)
                .claim("role", role)
                .subject(uid)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiration * 1000))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new com.srs.live.common.exception.AuthException("invalid or expired token");
        }
    }
}