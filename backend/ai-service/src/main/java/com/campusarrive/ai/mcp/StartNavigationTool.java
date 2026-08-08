package com.campusarrive.ai.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具二：调用校园导航（AID 7.1.2）。
 *
 * <p>规格来源：FR-01-16（MCP 工具-调用校园导航）。
 * 接收 poi_id（必填）、poi_name（选填）、student_location（选填），
 * 校验 POI 存在性后返回导航信息，供前端跳转校园导航页。</p>
 *
 * <p>参数校验（AID 7.3）：</p>
 * <ul>
 *   <li>poi_id 须非空且存在于 POI 数据库；</li>
 *   <li>poi_name 选填，用于导航页展示。</li>
 * </ul>
 */
public class StartNavigationTool implements McpTool {

    /** 工具名称。 */
    public static final String TOOL_NAME = "start_navigation";

    private final PoiStore poiStore;

    public StartNavigationTool(PoiStore poiStore) {
        this.poiStore = poiStore;
    }

    @Override
    public McpToolDefinition definition() {
        return new McpToolDefinition(
                TOOL_NAME,
                "启动校园导航至指定 POI",
                List.of(
                        new McpToolParam("poi_id", "string", true,
                                "校园 POI 唯一标识"),
                        new McpToolParam("poi_name", "string", false,
                                "POI 名称，用于导航页展示"),
                        new McpToolParam("student_location", "object", false,
                                "新生当前位置坐标 {lat, lng}")
                )
        );
    }

    @Override
    public McpToolResult execute(McpToolRequest request) {
        Map<String, Object> params = request.params();
        if (params == null) {
            return McpToolResult.failure("INVALID_PARAM", "参数不能为空");
        }
        Object poiIdRaw = params.get("poi_id");
        if (poiIdRaw == null || String.valueOf(poiIdRaw).isBlank()) {
            return McpToolResult.failure("INVALID_PARAM", "poi_id 不能为空");
        }

        String poiId = String.valueOf(poiIdRaw);
        if (!poiStore.exists(poiId)) {
            return McpToolResult.failure("NOT_FOUND",
                    "POI 不存在: " + poiId);
        }

        PoiInfo poi = poiStore.findById(poiId).orElseThrow();
        Map<String, Object> data = new HashMap<>();
        data.put("poi_id", poi.poiId());
        data.put("poi_name", poi.poiName());
        data.put("distance", poi.distance());
        data.put("nav_url", poi.navUrl());
        return McpToolResult.success(data);
    }
}
