package com.kkiri.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long accessTokenExpiration; // Access Token 만료 시간 (30분)

    @Value("${jwt.refresh_expiration}")
    private long refreshTokenExpiration; // Refresh Token 만료 시간 (14일)

    // [1] AccessToken 생성
    // 파라미터 이름을 email 대신 identifier(ID 또는 이메일)로 변경하여 범용성을 높입니다.
    public String generateAccessToken(String identifier, int userId, boolean isVerified) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userid", userId);
        claims.put("isVerified", isVerified);
        claims.put("type", "access");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(identifier)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // ✅ 최신 방식
                .compact();
    }

    // [2] RefreshToken 생성: AccessToken 재발급용 (최소한의 정보만 담음)
    public String generateRefreshToken(String identifier, int userId, boolean isVerified) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userid", userId);
        claims.put("isVerified", isVerified);
        claims.put("type","refresh");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(identifier)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // ✅ 최신 방식
                .compact();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // [3] 임시 토큰 생성: 회원가입 대기용
    public String generateTempToken(String email, String name, String provider, String providerId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ROLE_TEMP");
        claims.put("name", name);
        claims.put("provider", provider);
        claims.put("providerId", providerId);

        return buildToken(claims, email, 1000 * 60 * 15); // 15분
    }

    // 공통 토큰 빌더 로직
    private String buildToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // [4] 토큰 검증 및 정보 추출
    public String extractIdentifier(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaimByKey(token, "role");
    }

    public String extractClaimByKey(String token, String key) {
        final Claims claims = extractAllClaims(token);
        Object value = claims.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    // 토큰 유효성 검사 (Access/Refresh 공통 사용 가능)
    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    // 토큰에서 userid추출	
    public int extractUserId(String token) {
    	try {
    		Claims claims = extractAllClaims(token);
    		Object userId = claims.get("userid");
    		
    		return userId != null ? Integer.parseInt(String.valueOf(userId)) : 0;
    	} catch(Exception e) {
    		System.out.println("❌ JWT 추출 에러: " + e.getMessage());
    		return 0;
    	}
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
 // [추가] Access Token의 남은 만료 시간(초) 반환
    // 만료됐거나 오류 시 0 반환
    public long getTokenRemainingSeconds(String token) {
        try {
            Date expiration = extractClaim(token, Claims::getExpiration);
            long remainMs = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0, remainMs / 1000);
        } catch (Exception e) {
            return 0;
        }
    }
}