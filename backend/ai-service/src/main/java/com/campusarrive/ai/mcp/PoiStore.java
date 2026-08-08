package com.campusarrive.ai.mcp;

import java.util.List;
import java.util.Optional;

/**
 * 校园 POI 数据存储（AID 7.1.2 start_navigation 参数校验依赖）。
 *
 * <p>规格来源：FR-01-16（MCP 工具-调用校园导航）。
 * 提供 POI 查询能力，工具调用时校验 poi_id 是否存在。
 * 开发环境为内存实现，数据来源于种子知识库的 POI 分类。</p>
 */
public interface PoiStore {

    /**
     * 按 ID 查找 POI。
     *
     * @param poiId POI 唯一标识
     * @return POI 信息；不存在返回 empty
     */
    Optional<PoiInfo> findById(String poiId);

    /** 列出全部 POI。 */
    List<PoiInfo> findAll();

    /** 判断 POI 是否存在。 */
    boolean exists(String poiId);
}
