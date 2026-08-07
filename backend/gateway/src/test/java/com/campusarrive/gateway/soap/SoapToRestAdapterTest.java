package com.campusarrive.gateway.soap;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CT-MW-004：SOAP↔REST 协议适配契约测试（骨架）。
 *
 * <p>规格来源：FR-04-04 — SOAP 接口适配为 REST。
 * 当前 {@link SoapToRestAdapter} 仅为骨架实现，本测试类暂标记 {@link Disabled}，
 * 待 MW-2.2 迭代完成适配逻辑后启用。</p>
 */
@DisplayName("CT-MW-004: SOAP↔REST 协议适配")
class SoapToRestAdapterTest {

    private final SoapToRestAdapter adapter = new SoapToRestAdapter();

    @Disabled("SOAP 适配待实现 (FR-04-04)，暂留测试骨架")
    @Test
    @DisplayName("SOAP 请求转换为 REST JSON 调用")
    void testSoapToRestConversion() {
        // Arrange：构造 SOAP 信封
        String soapXml = """
                <?xml version="1.0"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <GetStudentStatus>
                      <studentId>STU20260001</studentId>
                    </GetStudentStatus>
                  </soap:Body>
                </soap:Envelope>
                """;

        // Act
        // String restJson = adapter.convertSoapToRest(soapXml);

        // Assert：转换结果应为 REST JSON，包含操作名与参数
        // TODO: 待 SoapToRestAdapter 实现后补充断言
    }

    @Disabled("SOAP 适配待实现 (FR-04-04)，暂留测试骨架")
    @Test
    @DisplayName("从 SOAP 请求提取操作名")
    void testExtractOperation() {
        // Arrange
        String soapXml = "<soap:Body><GetStudentStatus/></soap:Body>";

        // Act
        // String operation = adapter.extractOperation(soapXml);

        // Assert
        // assertEquals("GetStudentStatus", operation);
        // TODO: 待实现后启用
    }
}
