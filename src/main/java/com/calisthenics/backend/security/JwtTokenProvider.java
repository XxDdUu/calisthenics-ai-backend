package com.calisthenics.backend.security;

import com.calisthenics.backend.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_CLAIM = "type";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final AppProperties appProperties;
    private final SecretKey key;

    public JwtTokenProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
        String secret = appProperties.getJwt().getSecret().trim();
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            log.warn("JWT secret is not valid Base64; using raw bytes instead.");
            keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret key must be at least 256 bits (32 bytes). Current length: " + keyBytes.length + " bytes.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return generateAccessTokenFromUserPrincipal(userPrincipal);
    }

    public String generateAccessTokenFromUserPrincipal(UserPrincipal userPrincipal) {
        return generateToken(userPrincipal, appProperties.getJwt().getExpirationMs(), TOKEN_TYPE_ACCESS);
    }

    public String generateRefreshToken(UserPrincipal userPrincipal) {
        return generateToken(userPrincipal, appProperties.getJwt().getRefreshExpirationMs(), TOKEN_TYPE_REFRESH);
    }

    public String generateToken(UserPrincipal userPrincipal, long durationMs, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + durationMs);

        JwtBuilder builder = Jwts.builder()
                .subject(Long.toString(userPrincipal.getId()))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key);

        if (TOKEN_TYPE_ACCESS.equals(tokenType)) {
            String roles = userPrincipal.getAuthorities() != null
                    ? userPrincipal.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.joining(","))
                    : "";
            builder.claim("email", userPrincipal.getEmail())
                    .claim("fullName", userPrincipal.getFullName())
                    .claim("roles", roles);
        }

        return builder.compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromJWT(String token) {
        Claims claims = getClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public JwtValidationResult validateTokenWithResult(String authToken) {
        if (!StringUtils.hasText(authToken)) {
            return JwtValidationResult.EMPTY;
        }
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken);
            return JwtValidationResult.VALID;
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT token: {}", e.getMessage());
            return JwtValidationResult.EXPIRED;
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            return JwtValidationResult.INVALID_SIGNATURE;
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
            return JwtValidationResult.MALFORMED;
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
            return JwtValidationResult.UNSUPPORTED;
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
            return JwtValidationResult.EMPTY;
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return JwtValidationResult.MALFORMED;
        }
    }

    public boolean validateToken(String authToken) {
        return validateTokenWithResult(authToken) == JwtValidationResult.VALID;
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = getClaims(token);
            String type = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (type != null) {
                return TOKEN_TYPE_REFRESH.equalsIgnoreCase(type);
            }
            // Fallback for legacy tokens without type claim:
            // Refresh tokens did not have email or roles claims
            return claims.get("roles") == null && claims.get("email") == null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = getClaims(token);
            String type = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (type != null) {
                return TOKEN_TYPE_ACCESS.equalsIgnoreCase(type);
            }
            // Fallback for legacy tokens:
            // Access tokens had roles or email claims
            return claims.get("roles") != null || claims.get("email") != null;
        } catch (Exception e) {
            return false;
        }
    }

    public JwtValidationResult validateAccessToken(String token) {
        JwtValidationResult result = validateTokenWithResult(token);
        if (result != JwtValidationResult.VALID) {
            return result;
        }
        if (!isAccessToken(token)) {
            return JwtValidationResult.INVALID_TYPE;
        }
        return JwtValidationResult.VALID;
    }

    public JwtValidationResult validateRefreshToken(String token) {
        JwtValidationResult result = validateTokenWithResult(token);
        if (result != JwtValidationResult.VALID) {
            return result;
        }
        if (!isRefreshToken(token)) {
            return JwtValidationResult.INVALID_TYPE;
        }
        return JwtValidationResult.VALID;
    }
}
