package com.payflow.user.auth;

import com.payflow.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMillis
    ) {
        // HMAC 서명 키는 모든 서비스가 같은 jwt.secret을 알고 있어야 검증할 수 있다.
        // secret 길이가 짧으면 jjwt가 예외를 던지므로 운영에서는 충분히 긴 랜덤 문자열을 사용해야 한다.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMillis);

        // subject에는 표준적으로 토큰의 주체를 넣는다. 여기서는 userId를 문자열로 저장한다.
        // phoneNumber와 role은 게이트웨이가 하위 서비스로 X-User-* 헤더를 만들 때 사용한다.
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("phoneNumber", user.getPhoneNumber())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}
