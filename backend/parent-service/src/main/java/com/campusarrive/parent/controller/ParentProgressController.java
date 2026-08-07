package com.campusarrive.parent.controller;

import com.campusarrive.parent.model.ApiResponse;
import com.campusarrive.parent.model.ProgressResponseData;
import com.campusarrive.parent.model.ProgressResponseData.StepInfo;
import com.campusarrive.parent.service.ParentJwtService;
import com.campusarrive.parent.service.ProgressStore;
import com.campusarrive.parent.service.ProgressStore.ArrivalStatus;
import com.campusarrive.parent.service.ProgressStore.StudentProgress;
import com.campusarrive.parent.service.ProgressStore.StepStatus;
import com.campusarrive.parent.service.TokenRevocationStore;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 家长端报到进度查询控制器。
 *
 * <p>规格来源：FR-03-03 / API 6.2 节 —
 * 家长通过 JWT 令牌查询孩子的报到进度，返回脱敏后的进度信息。</p>
 *
 * <p>接口列表：
 * <ul>
 *   <li>GET /api/v1/parent/progress — 查询报到进度</li>
 * </ul></p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/parent")
@RequiredArgsConstructor
public class ParentProgressController {

    private final ParentJwtService parentJwtService;
    private final ProgressStore progressStore;
    private final TokenRevocationStore tokenRevocationStore;

    /** 日期格式化器（仅显示日期部分）。 */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    /**
     * 查询报到进度。
     *
     * <p>校验 JWT 令牌 → 提取 student_id → 查询进度 → 脱敏返回。</p>
     *
     * @param authHeader Authorization 请求头（Bearer 令牌）
     * @return 报到进度信息
     */
    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<ProgressResponseData>> getProgress(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        String requestId = getRequestId(httpRequest);

        // 1. 校验令牌
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40100, "缺少有效的 Authorization 令牌", requestId));
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = parentJwtService.parseToken(token);
        } catch (Exception e) {
            log.warn("[{}] 令牌解析失败: {}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40100, "令牌无效或已过期", requestId));
        }

        // 2. 检查令牌吊销
        String jti = claims.getId();
        if (tokenRevocationStore.isRevoked(jti)) {
            log.warn("[{}] 令牌已吊销: jti={}", requestId, jti);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40101, "令牌已被吊销", requestId));
        }

        // 3. 提取 student_id
        String studentId = claims.get("student_id", String.class);
        if (studentId == null || studentId.isEmpty()) {
            log.warn("[{}] 令牌中缺少 student_id: jti={}", requestId, jti);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40100, "令牌载荷无效", requestId));
        }

        // 4. 查询进度
        var progressOpt = progressStore.findByStudentId(studentId);
        if (progressOpt.isEmpty()) {
            log.warn("[{}] 学生进度记录不存在: studentId={}", requestId, studentId);
            return ResponseEntity.ok(ApiResponse.error(40401,
                    "未找到该学生的报到进度记录", requestId));
        }

        // 5. 构建脱敏响应
        StudentProgress progress = progressOpt.get();
        ProgressResponseData data = buildResponseData(progress);

        log.info("[{}] 进度查询成功: studentId={}, arrivalStatus={}",
                requestId, studentId, data.getArrivalStatus());
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ─── 内部方法 ──────────────────────────────────────────────

    /**
     * 构建进度响应数据（含脱敏处理）。
     */
    private ProgressResponseData buildResponseData(StudentProgress progress) {
        // 构建环节清单
        List<StepInfo> stepInfos = new ArrayList<>();
        int completedCount = 0;
        for (StepStatus status : progress.steps()) {
            StepInfo info = StepInfo.builder()
                    .stepCode(status.step().getCode())
                    .stepName(status.step().getDisplayName())
                    .completed(status.completed())
                    .completedAt(formatDate(status.timestamp()))
                    .build();
            stepInfos.add(info);
            if (status.completed()) {
                completedCount++;
            }
        }

        // 计算到校状态
        ArrivalStatus arrivalStatus = progressStore.getArrivalStatus(progress);
        int totalSteps = progress.steps().size();
        int progressPercent = totalSteps > 0
                ? (completedCount * 100 / totalSteps) : 0;

        return ProgressResponseData.builder()
                .studentId(progress.studentId())
                .studentNameMasked(maskName(progress.studentName()))
                .arrivalStatus(arrivalStatus.name().toLowerCase())
                .arrivalStatusDisplay(arrivalStatus.getDisplayName())
                .steps(stepInfos)
                .pendingMaterials(progressStore.getPendingMaterials(progress))
                .completedSteps(completedCount)
                .totalSteps(totalSteps)
                .progressPercent(progressPercent)
                .build();
    }

    /**
     * 格式化时间戳为日期字符串（仅显示日期）。
     */
    private String formatDate(Instant timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_FORMATTER.format(timestamp);
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

    private String getRequestId(HttpServletRequest request) {
        String id = request.getHeader("X-Request-Id");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
