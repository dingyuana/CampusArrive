package com.campusarrive.ai.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-AI-004: MCP 工具-调用校园导航契约测试。
 *
 * <p>规格来源：FR-01-16、AID 7.1.2。
 * 验证 {@code start_navigation} 工具的输入/输出契约：
 * 起终点正确（poi_id → poi_name + nav_url + distance），
 * 不存在的 poi_id 被拒，可选参数正确处理。</p>
 */
@DisplayName("CT-AI-004: MCP 工具-调用校园导航")
class StartNavigationContractTest {

    private StartNavigationTool tool;
    private InMemoryPoiStore poiStore;

    @BeforeEach
    void setUp() {
        poiStore = new InMemoryPoiStore();
        tool = new StartNavigationTool(poiStore);
    }

    @Test
    @DisplayName("导航至图书馆:poi_id=library_main → 返回正确导航信息")
    void navigateToLibrary() {
        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_id", "library_main"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success(), "合法 poi_id 应成功");
        assertEquals("library_main", result.data().get("poi_id"));
        assertEquals("中心图书馆", result.data().get("poi_name"));
        assertEquals(300, result.data().get("distance"));
        assertEquals("/pages/map/nav?dest=library_main", result.data().get("nav_url"));
    }

    @Test
    @DisplayName("导航至第一食堂:poi_id=canteen_1")
    void navigateToCanteen1() {
        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_id", "canteen_1"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success());
        assertEquals("canteen_1", result.data().get("poi_id"));
        assertEquals("第一食堂", result.data().get("poi_name"));
        assertEquals(320, result.data().get("distance"));
    }

    @Test
    @DisplayName("导航至校医院:poi_id=hospital")
    void navigateToHospital() {
        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_id", "hospital"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success());
        assertEquals("hospital", result.data().get("poi_id"));
        assertEquals("校医院", result.data().get("poi_name"));
        assertEquals(600, result.data().get("distance"));
    }

    @Test
    @DisplayName("不存在的 poi_id 被拒")
    void nonExistentPoiRejected() {
        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_id", "fake_poi"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertFalse(result.success());
        assertEquals("NOT_FOUND", result.errorCode());
        assertTrue(result.errorMessage().contains("fake_poi"));
    }

    @Test
    @DisplayName("缺少 poi_id 被拒")
    void missingPoiIdRejected() {
        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_name", "图书馆"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertFalse(result.success());
        assertEquals("INVALID_PARAM", result.errorCode());
    }

    @Test
    @DisplayName("null 参数被拒")
    void nullParamsRejected() {
        McpToolRequest req = new McpToolRequest(
                "start_navigation", null, "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertFalse(result.success());
    }

    @Test
    @DisplayName("nav_url 格式正确:含 dest 参数")
    void navUrlFormatCorrect() {
        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_id", "canteen_2"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success());
        String navUrl = (String) result.data().get("nav_url");
        assertTrue(navUrl.contains("dest=canteen_2"), "nav_url 应含 dest 参数");
    }

    @Test
    @DisplayName("自定义 POI 注册后可导航")
    void customPoiNavigable() {
        poiStore.register("test_building", "测试楼", "教学楼", 100);

        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_id", "test_building"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success());
        assertEquals("测试楼", result.data().get("poi_name"));
        assertEquals(100, result.data().get("distance"));
    }
}
