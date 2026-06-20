package com.payflow.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class GatewayRequestFilter extends OncePerRequestFilter {

    @Value("${gateway.internal-secret:}")
    private String gatewayInternalSecret;

    @Value("${internal.secret:}")
    private String internalSecret;

    @Value("${gateway.request-filter.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled || request.getRequestURI().startsWith("/actuator") || hasTrustedSecret(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    private boolean hasTrustedSecret(HttpServletRequest request) {
        // [C-1] 시크릿이 설정되지 않은 경우 fail-closed: 모든 요청을 거부한다.
        // 이전에는 return true(허용)였으나, 설정 누락 시 내부 API가 무방비 노출되는 취약점이 있었다.
        if (!StringUtils.hasText(gatewayInternalSecret) && !StringUtils.hasText(internalSecret)) {
            return false;
        }
        String gatewaySecret = request.getHeader("X-Gateway-Secret");
        String serviceSecret = request.getHeader("X-Internal-Secret");
        // [C-2] 타이밍 공격 방지를 위해 상수 시간 비교(MessageDigest.isEqual)를 사용한다.
        // String.equals()는 문자 불일치 시점에 즉시 반환하므로 응답 시간으로 시크릿 길이/내용을 추측할 수 있다.
        return (StringUtils.hasText(gatewayInternalSecret) && constantTimeEquals(gatewayInternalSecret, gatewaySecret))
                || (StringUtils.hasText(internalSecret) && constantTimeEquals(internalSecret, serviceSecret));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
