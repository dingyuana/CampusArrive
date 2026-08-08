package com.campusarrive.ai.mcp;

/**
 * 校园 POI 信息（AID 7.1.2 start_navigation 返回值）。
 *
 * <p>规格来源：FR-01-16（MCP 工具-调用校园导航）。
 * POI 数据来源于知识库 POI 分类（{@link com.campusarrive.ai.knowledge.KnowledgeCategory#POI}），
 * 工具调用时校验 poi_id 是否存在并返回导航信息。</p>
 *
 * @param poiId    POI 唯一标识
 * @param poiName  POI 名称（如"中心图书馆"）
 * @param category POI 分类（如"食堂"、"图书馆"、"医院"）
 * @param distance 距报到处距离（米）
 * @param navUrl   导航页 URL
 */
public record PoiInfo(
        String poiId,
        String poiName,
        String category,
        int distance,
        String navUrl
) {

    /** 构造导航 URL。 */
    public static String buildNavUrl(String poiId) {
        return "/pages/map/nav?dest=" + poiId;
    }
}
