package com.campusarrive.ai.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 POI 存储（开发环境占位）。
 *
 * <p>规格来源：FR-01-16。
 * POI 数据来源于种子知识库的 POI 分类（食堂、图书馆、校医院等）。
 * 生产环境替换为数据库查询。</p>
 */
public class InMemoryPoiStore implements PoiStore {

    private final Map<String, PoiInfo> pois = new ConcurrentHashMap<>();

    public InMemoryPoiStore() {
        seedDefaultPois();
    }

    private void seedDefaultPois() {
        // 与 SeedKnowledgeBase 的 POI 数据保持一致
        register("canteen_1", "第一食堂", "食堂", 320);
        register("canteen_2", "第二食堂", "食堂", 580);
        register("canteen_halal", "清真食堂", "食堂", 450);
        register("library_main", "中心图书馆", "图书馆", 300);
        register("hospital", "校医院", "医院", 600);
    }

    /** 注册 POI（测试与初始化用）。 */
    public void register(String poiId, String poiName, String category, int distance) {
        pois.put(poiId, new PoiInfo(poiId, poiName, category, distance,
                PoiInfo.buildNavUrl(poiId)));
    }

    @Override
    public Optional<PoiInfo> findById(String poiId) {
        if (poiId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pois.get(poiId));
    }

    @Override
    public List<PoiInfo> findAll() {
        return List.copyOf(pois.values());
    }

    @Override
    public boolean exists(String poiId) {
        return poiId != null && pois.containsKey(poiId);
    }
}
