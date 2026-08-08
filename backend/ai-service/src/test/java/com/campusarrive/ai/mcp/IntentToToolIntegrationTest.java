package com.campusarrive.ai.mcp;

import com.campusarrive.ai.chat.workflow.IntentMarker;
import com.campusarrive.ai.chat.workflow.LocalDeepSeekGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 意图标记到 MCP 工具调用集成测试。
 *
 * <p>规格来源：AID 7.2 调用流程 — 模型输出意图标记 → 前端解析 → 调用 MCP 工具。
 * 验证 AI-3.2 的 {@code IntentMarker} 与 AI-3.3 的 MCP 工具映射链路：
 * DeepSeek 回复中的 {@code [[STEP:payment]]} 标记可被解析并映射到
 * {@code navigate_to_step} 工具调用。</p>
 */
@DisplayName("AT-AI: 意图标记 → MCP 工具调用集成")
class IntentToToolIntegrationTest {

    private McpToolService toolService;
    private InMemoryMcpToolLogStore logStore;

    @BeforeEach
    void setUp() {
        InMemoryMcpToolRegistry registry = new InMemoryMcpToolRegistry();
        InMemoryPoiStore poiStore = new InMemoryPoiStore();
        logStore = new InMemoryMcpToolLogStore();
        registry.register(new NavigateToStepTool());
        registry.register(new StartNavigationTool(poiStore));
        toolService = new McpToolService(registry, new McpToolRateLimiter(), logStore);
    }

    @Test
    @DisplayName("STEP 意图标记 → navigate_to_step 工具调用")
    void stepIntentToToolCall() {
        // 模拟 DeepSeek 回复含意图标记
        String aiReply = "学费可在财务处缴纳。\n[[STEP:payment]]";

        // 1. 解析意图标记（AI-3.2 逻辑）
        List<IntentMarker> intents = LocalDeepSeekGenerator.extractIntents(aiReply);
        assertEquals(1, intents.size());
        IntentMarker intent = intents.get(0);
        assertEquals("STEP", intent.type());
        assertEquals("payment", intent.target());

        // 2. 映射到 MCP 工具调用
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", intent.target(), "student_id", "20260001"),
                "20260001", "sess-integration");

        McpToolResult result = toolService.invoke(req);

        assertTrue(result.success(), "STEP 意图应成功映射到工具调用");
        assertEquals("payment", result.data().get("step_id"));
        assertEquals("/pages/checkin/pay/index", result.data().get("page_url"));
    }

    @Test
    @DisplayName("POI 意图标记 → start_navigation 工具调用")
    void poiIntentToToolCall() {
        // 模拟 DeepSeek 回复含 POI 意图标记
        // LocalDeepSeekGenerator 输出 [[POI:section_name]]，section 可能是中文
        // 这里用 poi_id 映射：图书馆 → library_main
        String aiReply = "中心图书馆位于校园中区。\n[[POI:library_main]]";

        List<IntentMarker> intents = LocalDeepSeekGenerator.extractIntents(aiReply);
        assertEquals(1, intents.size());
        assertEquals("POI", intents.get(0).type());
        assertEquals("library_main", intents.get(0).target());

        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_id", intents.get(0).target()),
                "20260001", "sess-integration");

        McpToolResult result = toolService.invoke(req);

        assertTrue(result.success(), "POI 意图应成功映射到工具调用");
        assertEquals("library_main", result.data().get("poi_id"));
        assertEquals("中心图书馆", result.data().get("poi_name"));
    }

    @Test
    @DisplayName("无意图标记的回复不触发工具调用")
    void noIntentNoToolCall() {
        String aiReply = "本科新生报到需携带录取通知书等材料。";

        List<IntentMarker> intents = LocalDeepSeekGenerator.extractIntents(aiReply);
        assertTrue(intents.isEmpty(), "无意图标记的回复不应触发工具调用");
    }

    @Test
    @DisplayName("工具调用后审计日志可追溯")
    void toolCallAuditable() {
        String aiReply = "请前往校医院体检。\n[[STEP:checkin]]";

        List<IntentMarker> intents = LocalDeepSeekGenerator.extractIntents(aiReply);
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", intents.get(0).target(), "student_id", "20260001"),
                "20260001", "sess-audit-trail");

        toolService.invoke(req);

        List<McpToolLog> logs = logStore.findByStudent("20260001");
        assertEquals(1, logs.size());
        assertEquals("navigate_to_step", logs.get(0).toolName());
        assertTrue(logs.get(0).success());
    }
}
