package com.payflow.gateway.filter;

import com.payflow.gateway.auth.AuthenticatedUser;
import com.payflow.gateway.auth.JwtTokenProvider;
import com.payflow.gateway.support.error.BusinessException;
import com.payflow.gateway.support.error.ErrorCode;
import com.payflow.gateway.support.error.ErrorResponse;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_USER_ID = "X-User-Id";
    private static final String X_USER_EMAIL = "X-User-Email";
    private static final String X_USER_ROLE = "X-User-Role";
    private static final String X_INTERNAL_REQUEST = "X-Internal-Request";
    private static final String X_INTERNAL_SECRET = "X-Internal-Secret";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = removeUserHeaders(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        if (isPublicRequest(sanitizedRequest)) {
            return chain.filter(sanitizedExchange);
        }

        try {
            String token = resolveToken(sanitizedRequest);
            AuthenticatedUser user = jwtTokenProvider.parse(token);
            ServerHttpRequest authenticatedRequest = sanitizedRequest.mutate()
                    .header(X_USER_ID, String.valueOf(user.userId()))
                    .header(X_USER_EMAIL, user.email())
                    .header(X_USER_ROLE, user.role())
                    .build();
            return chain.filter(sanitizedExchange.mutate().request(authenticatedRequest).build());
        } catch (BusinessException exception) {
            return writeError(sanitizedExchange.getResponse(), exception.getErrorCode(), exception.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private ServerHttpRequest removeUserHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(X_USER_ID);
                    headers.remove(X_USER_EMAIL);
                    headers.remove(X_USER_ROLE);
                    headers.remove(X_INTERNAL_REQUEST);
                    headers.remove(X_INTERNAL_SECRET);
                })
                .build();
    }

    private boolean isPublicRequest(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        return path.startsWith("/actuator")
                || (method == HttpMethod.POST && "/api/users".equals(path))
                || (method == HttpMethod.POST && "/api/users/login".equals(path));
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authorization.substring(BEARER_PREFIX.length());
    }

    private Mono<Void> writeError(ServerHttpResponse response, ErrorCode errorCode, String message) {
        ErrorResponse errorResponse = ErrorResponse.of(errorCode, message);
        response.setStatusCode(errorCode.getStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"code":"%s","message":"%s","timestamp":"%s"}
                """.formatted(
                errorResponse.code(),
                escapeJson(errorResponse.message()),
                errorResponse.timestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
