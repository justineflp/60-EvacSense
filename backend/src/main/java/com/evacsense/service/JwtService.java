package com.evacsense.service;

import com.evacsense.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final String DEFAULT_SECRET = "evacsense_ccs_secret_jwt_key_2026_super_long";
    private final Key signingKey;

    public JwtService() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.length() < 32) {
            System.err.println("[SECURITY] WARNING: JWT_SECRET environment variable is not set or too short. " +
                    "Using default secret — set JWT_SECRET in production to prevent token forgery.");
            secret = DEFAULT_SECRET;
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String signToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("name", user.getName());
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole());
        claims.put("department", user.getDepartment());

        long oneHourInMillis = 3600000;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + oneHourInMillis);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getId())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims verifyToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new RuntimeException("Token has expired. Please log in again.");
        } catch (Exception e) {
            throw new RuntimeException("Invalid security token.");
        }
    }
}
