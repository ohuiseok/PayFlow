package com.payflow.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.gateway.auth.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "payflow-test-secret-key-must-be-long-enough-1234567890";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtTokenProvider(SECRET));

    @Test
    void publicRequestRemovesExternalUserHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/users")
                .header("X-User-Id", "999")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, captureExchange(captured)).block();

        assertThat(captured.get().getRequest().getHeaders().containsHeader("X-User-Id")).isFalse();
    }

    @Test
    void protectedRequestAddsAuthenticatedUserHeaders() {
        String token = createToken(1L, "user@example.com", "USER");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "999")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, captureExchange(captured)).block();

        HttpHeaders headers = captured.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("1");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void protectedRequestWithoutTokenReturnsUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, "/api/users/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, captureExchange(new AtomicReference<>())).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    private GatewayFilterChain captureExchange(AtomicReference<ServerWebExchange> captured) {
        return exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
    }

    private String createToken(Long userId, String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(key)
                .compact();
    }
}
