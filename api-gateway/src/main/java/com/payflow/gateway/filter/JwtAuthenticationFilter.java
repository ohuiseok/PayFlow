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
    private static final String X_USER_PHONE_NUMBER = "X-User-Phone-Number";
    private static final String X_USER_ROLE = "X-User-Role";
    private static final String X_INTERNAL_REQUEST = "X-Internal-Request";
    private static final String X_INTERNAL_SECRET = "X-Internal-Secret";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 외부 클라이언트가 X-User-Id 같은 내부 신뢰 헤더를 직접 보낼 수 있으므로 먼저 제거한다.
        // 이후 JWT 검증에 성공한 값만 게이트웨이가 다시 넣어 하위 서비스로 전달한다.
        ServerHttpRequest sanitizedRequest = removeUserHeaders(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        // 회원가입/로그인/헬스체크는 토큰이 없어야 접근 가능하다.
        // 이 외의 API는 모두 Authorization: Bearer {token}을 요구한다.
        if (isPublicRequest(sanitizedRequest)) {
            return chain.filter(sanitizedExchange);
        }

        try {
            String token = resolveToken(sanitizedRequest);
            AuthenticatedUser user = jwtTokenProvider.parse(token);
            // 하위 서비스는 JWT 라이브러리를 몰라도 된다.
            // 게이트웨이가 검증한 사용자 정보를 헤더로 전달하면 서비스들은 이 헤더를 권한 판단 입력으로 사용한다.
            ServerHttpRequest authenticatedRequest = sanitizedRequest.mutate()
                    .header(X_USER_ID, String.valueOf(user.userId()))
                    .header(X_USER_PHONE_NUMBER, user.phoneNumber())
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
        // 보안 경계에서 가장 먼저 해야 할 일은 "클라이언트가 조작 가능한 신뢰 정보"를 버리는 것이다.
        // 특히 X-Internal-* 헤더는 서비스 간 호출 여부를 나타내므로 외부에서 들어온 값은 절대 신뢰하면 안 된다.
        return request.mutate()
                .headers(headers -> {
                    headers.remove(X_USER_ID);
                    headers.remove(X_USER_PHONE_NUMBER);
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
        // Authorization 헤더는 "Bearer " 접두어를 사용한다.
        // 접두어가 없거나 토큰이 없으면 JWT 파싱을 시도하지 않고 인증 실패로 처리한다.
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authorization.substring(BEARER_PREFIX.length());
    }

    private Mono<Void> writeError(ServerHttpResponse response, ErrorCode errorCode, String message) {
        // Spring Cloud Gateway는 WebFlux 기반이라 응답도 Mono<Void>로 비동기 작성한다.
        // 필터에서 예외를 그대로 던지기보다 여기서 JSON 에러 응답을 직접 만들어 클라이언트 형식을 맞춘다.
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
        // 직접 JSON 문자열을 만들고 있으므로 따옴표와 역슬래시는 반드시 escape해야 한다.
        // ObjectMapper를 사용하면 더 안전하지만, 현재 구조에서는 최소한의 JSON 깨짐을 방지한다.
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
