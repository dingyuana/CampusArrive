package com.campusarrive.gateway.security.pii;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * PII 响应脱敏过滤器（展示层防护）。
 *
 * <p>规格来源：SCS-CA-2026-09 第 3.2 节 PII 五层防护第 4 层 —
 * 网关在 API 响应返回前，经响应脱敏中间件依据字段分级标注自动脱敏：
 * 身份证号显示前 3 后 4、手机号显示前 3 后 4、家庭住址仅显示到区县级。
 * 仅在审批授权的运维终端才可查看完整 PII。</p>
 *
 * <p>本过滤器作为 Spring Cloud Gateway 全局过滤器，拦截 JSON 响应体，
 * 按字段名匹配 PII 类型并执行脱敏，确保客户端不可见明文 L3 敏感数据。</p>
 */
@Slf4j
@Component
public class PiiResponseFilter implements GlobalFilter, Ordered {

    /** 字段名 → PII 类型映射（覆盖常见命名风格）。 */
    private static final Map<String, PiiType> PII_FIELD_MAP = Map.ofEntries(
            Map.entry("idCard", PiiType.ID_CARD),
            Map.entry("id_card", PiiType.ID_CARD),
            Map.entry("identityNumber", PiiType.ID_CARD),
            Map.entry("idNumber", PiiType.ID_CARD),
            Map.entry("phone", PiiType.PHONE),
            Map.entry("mobile", PiiType.PHONE),
            Map.entry("phoneNumber", PiiType.PHONE),
            Map.entry("mobileNumber", PiiType.PHONE),
            Map.entry("name", PiiType.NAME),
            Map.entry("studentName", PiiType.NAME),
            Map.entry("parentName", PiiType.NAME),
            Map.entry("fullName", PiiType.NAME),
            Map.entry("email", PiiType.EMAIL),
            Map.entry("address", PiiType.ADDRESS),
            Map.entry("homeAddress", PiiType.ADDRESS),
            Map.entry("familyAddress", PiiType.ADDRESS),
            Map.entry("bankCard", PiiType.BANK_CARD),
            Map.entry("bankCardNumber", PiiType.BANK_CARD)
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decorator = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                MediaType contentType = originalResponse.getHeaders().getContentType();
                if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                    return super.writeWith(body);
                }
                return DataBufferUtils.join(body)
                        .map(this::processBody)
                        .flatMap(processed -> super.writeWith(Mono.just(processed)));
            }

            private DataBuffer processBody(DataBuffer dataBuffer) {
                try {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String json = new String(bytes, StandardCharsets.UTF_8);
                    String masked = maskPiiInJson(json);

                    byte[] maskedBytes = masked.getBytes(StandardCharsets.UTF_8);
                    originalResponse.getHeaders().setContentLength(maskedBytes.length);
                    return originalResponse.bufferFactory().wrap(maskedBytes);
                } catch (Exception e) {
                    log.warn("PII 响应脱敏失败，返回原始响应: {}", e.getMessage());
                    return dataBuffer;
                }
            }
        };
        return chain.filter(exchange.mutate().response(decorator).build());
    }

    /**
     * 对 JSON 字符串执行 PII 字段脱敏。
     *
     * <p>递归遍历 JSON 树，按字段名匹配 PII 类型并脱敏字符串值。
     * 非 JSON 或解析失败时原样返回。</p>
     *
     * @param json 原始 JSON 字符串
     * @return 脱敏后的 JSON 字符串
     */
    String maskPiiInJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isMissingNode()) {
                return json;
            }
            maskPiiInNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.debug("JSON 解析失败，跳过 PII 脱敏: {}", e.getMessage());
            return json;
        }
    }

    /**
     * 递归遍历 JSON 节点，对匹配 PII 字段名的字符串值执行脱敏。
     */
    private void maskPiiInNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();
                PiiType piiType = PII_FIELD_MAP.get(fieldName);
                if (piiType != null && value.isTextual()) {
                    obj.put(fieldName, PiiMasker.mask(value.asText(), piiType));
                } else if (value.isObject() || value.isArray()) {
                    maskPiiInNode(value);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                maskPiiInNode(element);
            }
        }
    }

    @Override
    public int getOrder() {
        // 在所有请求处理过滤器之后执行（响应脱敏为最后一步）
        return -400;
    }
}
