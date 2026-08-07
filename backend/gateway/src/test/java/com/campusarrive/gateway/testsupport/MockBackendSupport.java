package com.campusarrive.gateway.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Mock 后端服务支撑（测试基础设施）。
 *
 * <p>规格来源：CT-MW-001~003 — 契约测试需要下游服务接收网关转发请求。
 * 使用 JDK 内置 {@link HttpServer} 启动一个轻量 Mock 后端，
 * 根据请求路径返回对应服务名，并将网关注入的 X-Request-Id / X-User-Role 回显，
 * 供路由、鉴权、链路追踪测试断言。</p>
 *
 * <p>静态初始化块在类加载时启动服务（随机端口），JVM 关闭时自动停止，
 * 多个测试类共享同一实例以避免端口冲突。路由由 {@link MockRouteConfiguration} 以 Bean 形式指向本服务。</p>
 */
@Slf4j
public final class MockBackendSupport {

    private static final HttpServer SERVER;
    public static final int PORT;

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/", MockBackendSupport::handle);
            SERVER.setExecutor(Executors.newFixedThreadPool(8));
            SERVER.start();
            PORT = SERVER.getAddress().getPort();
            log.info("Mock 后端服务已启动: 127.0.0.1:{}", PORT);
        } catch (IOException e) {
            throw new IllegalStateException("无法启动 Mock 后端服务", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> SERVER.stop(0)));
    }

    private MockBackendSupport() {
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String service = resolveService(path);
        String requestId = header(exchange, "X-Request-Id");
        String role = header(exchange, "X-User-Role");
        String studentId = header(exchange, "X-Student-Id");

        String body = "{\"service\":\"" + service + "\""
                + ",\"request_id\":\"" + escape(requestId) + "\""
                + ",\"role\":\"" + escape(role) + "\""
                + ",\"student_id\":\"" + escape(studentId) + "\""
                + ",\"idCard\":\"110101199001011234\""
                + ",\"phone\":\"13812345678\""
                + ",\"name\":\"张三丰\""
                + ",\"email\":\"zhangsan@example.com\""
                + "}";

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String resolveService(String path) {
        if (path.startsWith("/api/v1/checkin")) {
            return "checkin-service";
        }
        if (path.startsWith("/api/v1/ai")) {
            return "ai-service";
        }
        if (path.startsWith("/api/v1/parent")) {
            return "parent-service";
        }
        if (path.startsWith("/api/v1/integration")) {
            return "integration-service";
        }
        if (path.startsWith("/api/v1/auth")) {
            return "checkin-service";
        }
        return "unknown";
    }

    private static String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value != null ? value : "";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "");
    }
}
