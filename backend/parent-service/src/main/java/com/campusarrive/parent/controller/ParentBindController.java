package com.campusarrive.parent.controller;

import com.campusarrive.parent.model.ApiResponse;
import com.campusarrive.parent.model.BindRequest;
import com.campusarrive.parent.model.BindResponseData;
import com.campusarrive.parent.model.VerifyCodeRequest;
import com.campusarrive.parent.service.ParentJwtService;
import com.campusarrive.parent.service.PreRegistrationStore;
import com.campusarrive.parent.service.TokenRevocationStore;
import com.campusarrive.parent.service.VerificationCodeService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 家长绑定鉴权控制器。
 *
 * <p>规格来源：FR-03-01 / FR-03-02 / API 6.1 节 —
 * 家长通过手机号 + 验证码绑定预登记关系，成功后下发 JWT 令牌（30 天有效期）。</p>
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/v1/parent/verify-code — 请求验证码</li>
 *   <li>POST /api/v1/parent/bind — 绑定并签发 JWT</li>
 *   <li>POST /api/v1/parent/revoke — 吊销令牌</li>
 * </ul></p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/parent")
@RequiredArgsConstructor
public class ParentBindController {

    private final VerificationCodeService verificationCodeService;
    private final PreRegistrationStore preRegistrationStore;
    private final ParentJwtService parentJwtService;
    private final TokenRevocationStore tokenRevocationStore;

    /**
     * 请求验证码。
     *
     * <p>校验手机号是否预登记 → 检查限频 → 生成并存储验证码。</p>
     *
     * @param request 验证码请求（含手机号）
     * @return 操作结果
     */
    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyCode(
            @Valid @RequestBody VerifyCodeRequest request,
            HttpServletRequest httpRequest) {
        String requestId = getRequestId(httpRequest);
        String phone = request.getPhone();

        // 1. 校验预登记
        if (preRegistrationStore.findByPhone(phone).isEmpty()) {
            log.info("[{}] 手机号未预登记: {}", requestId, phone);
            return ResponseEntity.ok(ApiResponse.error(40003,
                    "该手机号未在预登记名单中，无法绑定", requestId));
        }

        // 2. 检查限频
        if (verificationCodeService.isRateLimited(phone)) {
            log.warn("[{}] 限频触发: phone={}", requestId, phone);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(90004,
                            "绑定请求过于频繁，请稍后再试", requestId));
        }

        // 3. 生成并存储验证码
        verificationCodeService.recordRequest(phone, java.time.Instant.now());
        String code = verificationCodeService.generateAndStore(phone);

        log.info("[{}] 验证码已发送: phone={}", requestId, phone);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("sent", true, "expiry_seconds", 300), requestId));
    }

    /**
     * 绑定并签发 JWT。
     *
     * <p>校验验证码 → 签发 JWT（30 天）→ 返回令牌与关联学生信息。</p>
     *
     * @param request 绑定请求（含手机号 + 验证码）
     * @return 绑定响应（含 JWT 令牌）
     */
    @PostMapping("/bind")
    public ResponseEntity<ApiResponse<BindResponseData>> bind(
            @Valid @RequestBody BindRequest request,
            HttpServletRequest httpRequest) {
        String requestId = getRequestId(httpRequest);
        String phone = request.getPhone();
        String code = request.getVerifyCode();

        // 1. 校验预登记
        var preReg = preRegistrationStore.findByPhone(phone);
        if (preReg.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(40003,
                    "该手机号未在预登记名单中，无法绑定", requestId));
        }

        // 2. 检查锁定
        if (verificationCodeService.isLocked(phone)) {
            long remaining = verificationCodeService.getRemainingLockSeconds(phone);
            return ResponseEntity.ok(ApiResponse.error(40005,
                    "验证码错误次数过多，请 " + (remaining / 60 + 1) + " 分钟后重试", requestId));
        }

        // 3. 校验验证码
        var result = verificationCodeService.verify(phone, code);
        if (result instanceof VerificationCodeService.VerifyResult.Success) {
            // 4. 签发 JWT
            var reg = preReg.get();
            String token = parentJwtService.issueToken(reg.studentId(), phone);
            String bindId = "P" + UUID.randomUUID().toString().substring(0, 8);

            BindResponseData data = BindResponseData.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .expiresIn(parentJwtService.getExpirySeconds())
                    .studentId(reg.studentId())
                    .studentNameMasked(maskName(reg.studentName()))
                    .bindId(bindId)
                    .build();

            log.info("[{}] 绑定成功: phone={}, studentId={}", requestId, phone, reg.studentId());
            return ResponseEntity.ok(ApiResponse.success(data, requestId));
        }

        // 验证码校验失败
        return handleVerifyFailure(result, requestId);
    }

    /**
     * 吊销令牌。
     *
     * <p>规格来源：FR-03-02 — 令牌可吊销，已吊销令牌返回 401。</p>
     *
     * @param authHeader Authorization 请求头
     * @return 操作结果
     */
    @PostMapping("/revoke")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revoke(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        String requestId = getRequestId(httpRequest);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40100, "缺少有效的 Authorization 令牌", requestId));
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = parentJwtService.parseToken(token);
            String jti = claims.getId();
            String subject = claims.getSubject();

            tokenRevocationStore.revoke(jti, subject != null ? subject : "");
            log.info("[{}] 令牌已吊销: jti={}", requestId, jti);

            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("revoked", true), requestId));
        } catch (Exception e) {
            log.warn("[{}] 令牌吊销失败: {}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40100, "令牌无效或已过期", requestId));
        }
    }

    /**
     * 检查令牌是否有效（用于网关或客户端验证）。
     *
     * @param authHeader Authorization 请求头
     * @return 令牌状态
     */
    @GetMapping("/token/check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        String requestId = getRequestId(httpRequest);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40100, "缺少有效的 Authorization 令牌", requestId));
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = parentJwtService.parseToken(token);
            String jti = claims.getId();

            if (tokenRevocationStore.isRevoked(jti)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(40101, "令牌已被吊销", requestId));
            }

            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("valid", true, "student_id",
                            claims.get("student_id", String.class)), requestId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40100, "令牌无效或已过期", requestId));
        }
    }

    // ─── 内部工具方法 ──────────────────────────────────────────

    private String getRequestId(HttpServletRequest request) {
        String id = request.getHeader("X-Request-Id");
        return id != null ? id : UUID.randomUUID().toString();
    }

    /**
     * 姓名脱敏：2 字保留首字，3 字以上保留首尾。
     */
    private String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.length() == 1) {
            return "*";
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    private ResponseEntity<ApiResponse<BindResponseData>> handleVerifyFailure(
            VerificationCodeService.VerifyResult result, String requestId) {
        if (result instanceof VerificationCodeService.VerifyResult.Expired) {
            return ResponseEntity.ok(ApiResponse.error(40004,
                    "验证码错误或已过期", requestId));
        }
        if (result instanceof VerificationCodeService.VerifyResult.Wrong) {
            return ResponseEntity.ok(ApiResponse.error(40004,
                    "验证码错误或已过期", requestId));
        }
        if (result instanceof VerificationCodeService.VerifyResult.NotFound) {
            return ResponseEntity.ok(ApiResponse.error(40004,
                    "验证码错误或已过期", requestId));
        }
        if (result instanceof VerificationCodeService.VerifyResult.Locked) {
            return ResponseEntity.ok(ApiResponse.error(40005,
                    "验证码错误次数过多，请 30 分钟后重试", requestId));
        }
        return ResponseEntity.ok(ApiResponse.error(40004,
                "验证码校验失败", requestId));
    }
}
