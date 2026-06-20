package com.payflow.banking.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.banking.entity.TossPaymentStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "toss-payments", name = "mode", havingValue = "real")
public class RealTossPaymentsClient implements TossPaymentsClient {

    private final TossPaymentsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public RealTossPaymentsClient(TossPaymentsProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public TossPaymentResult confirm(String paymentKey, String orderId, BigDecimal amount) {
        String body = "{\"paymentKey\":\"" + escape(paymentKey)
                + "\",\"orderId\":\"" + escape(orderId)
                + "\",\"amount\":" + amount.toPlainString() + "}";
        return sendPaymentRequest("POST", "/v1/payments/confirm", body);
    }

    @Override
    public TossPaymentResult getPayment(String paymentKey) {
        return sendPaymentRequest("GET", "/v1/payments/" + encodePath(paymentKey), null);
    }

    @Override
    public TossPaymentCancelResult cancel(String paymentKey, String cancelReason, BigDecimal cancelAmount) {
        String body = "{\"cancelReason\":\"" + escape(cancelReason) + "\""
                + (cancelAmount == null ? "" : ",\"cancelAmount\":" + cancelAmount.toPlainString())
                + "}";
        TossPaymentResult result = sendPaymentRequest("POST", "/v1/payments/" + encodePath(paymentKey) + "/cancel", body);
        return new TossPaymentCancelResult(result, cancelAmount, null);
    }

    private TossPaymentResult sendPaymentRequest(String method, String path, String body) {
        if (!StringUtils.hasText(properties.secretKey())) {
            throw new IllegalStateException("Toss secret key is required for real mode");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.effectiveApiBaseUrl() + path))
                    .header("Authorization", basicAuth())
                    .header("Content-Type", "application/json");
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Toss API failed: " + response.statusCode() + " " + response.body());
            }
            return parsePayment(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Toss API request interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Toss API request failed", exception);
        }
    }

    private TossPaymentResult parsePayment(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode receipt = root.path("receipt");
        JsonNode checkout = root.path("checkout");
        return new TossPaymentResult(
                text(root, "paymentKey"),
                text(root, "orderId"),
                text(root, "orderName"),
                text(root, "method"),
                parseStatus(text(root, "status")),
                decimal(root, "totalAmount"),
                decimal(root, "balanceAmount"),
                parseDateTime(text(root, "approvedAt")),
                receipt.isMissingNode() ? null : text(receipt, "url"),
                checkout.isMissingNode() ? null : text(checkout, "url"),
                rawJson
        );
    }

    private TossPaymentStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return TossPaymentStatus.UNKNOWN;
        }
        try {
            return TossPaymentStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            return TossPaymentStatus.UNKNOWN;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String basicAuth() {
        String token = properties.secretKey() + ":";
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String encodePath(String value) {
        return value == null ? "" : value.replace("/", "%2F");
    }
}
