package com.campusarrive.gateway.soap;

import lombok.extern.slf4j.Slf4j;

/**
 * SOAP→REST 协议适配器（骨架实现）。
 *
 * <p>规格来源：FR-04-04 — 将遗留 SOAP 接口适配为 REST 调用。
 * 解析 SOAP XML body，提取操作名与参数，转换为 REST JSON 请求转发至下游。</p>
 *
 * <p>当前为骨架阶段，核心方法标记 TODO，待 MW-2.2 迭代实现。</p>
 */
@Slf4j
public class SoapToRestAdapter {

    /**
     * 将 SOAP XML 请求体转换为 REST JSON 请求体。
     *
     * @param soapXml SOAP 信封 XML 字符串
     * @return 转换后的 REST JSON 字符串
     */
    public String convertSoapToRest(String soapXml) {
        // TODO: 解析 SOAP Envelope/Body，提取操作名与参数，映射为 REST 资源路径与 JSON（FR-04-04）
        log.warn("SOAP→REST 适配尚未实现，原始 SOAP 将被丢弃");
        throw new UnsupportedOperationException("SOAP→REST 适配待实现 (FR-04-04)");
    }

    /**
     * 从 SOAP 请求中提取操作名（骨架）。
     *
     * @param soapXml SOAP XML 字符串
     * @return 操作名
     */
    public String extractOperation(String soapXml) {
        // TODO: 解析 Body 下首个子元素名作为操作名
        throw new UnsupportedOperationException("操作名提取待实现 (FR-04-04)");
    }
}
