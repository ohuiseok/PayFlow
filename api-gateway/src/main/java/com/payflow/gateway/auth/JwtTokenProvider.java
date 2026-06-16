package com.payflow.gateway.auth;

import com.payflow.gateway.support.error.BusinessException;
import com.payflow.gateway.support.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        // user-service가 토큰을 만들 때 사용한 secret과 같은 값이어야 서명 검증이 성공한다.
        // 게이트웨이는 토큰을 발급하지 않고 검증만 담당한다.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthenticatedUser parse(String token) {
        try {
            // parseSignedClaims는 서명을 검증한 뒤 payload를 반환한다.
            // 서명이 틀리거나 형식이 깨진 토큰은 JwtException으로 떨어진다.
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new AuthenticatedUser(
                    // subject에는 user-service가 넣은 userId가 들어 있다.
                    // 이후 필터에서 이 값을 X-User-Id 헤더로 변환한다.
                    Long.valueOf(claims.getSubject()),
                    claims.get("phoneNumber", String.class),
                    claims.get("role", String.class)
            );
        } catch (ExpiredJwtException exception) {
            // 만료와 잘못된 토큰을 구분하면 클라이언트가 재로그인/토큰 갱신 같은 처리를 더 정확히 할 수 있다.
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
}
