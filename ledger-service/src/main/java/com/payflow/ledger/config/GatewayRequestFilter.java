package com.payflow.ledger.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
        if (!StringUtils.hasText(gatewayInternalSecret) && !StringUtils.hasText(internalSecret)) {
            return true;
        }
        String gatewaySecret = request.getHeader("X-Gateway-Secret");
        String serviceSecret = request.getHeader("X-Internal-Secret");
        return (StringUtils.hasText(gatewayInternalSecret) && gatewayInternalSecret.equals(gatewaySecret))
                || (StringUtils.hasText(internalSecret) && internalSecret.equals(serviceSecret));
    }
}
