package com.campusarrive.parent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 家长消息推送服务。
 *
 * <p>规格来源：FR-03-06 — 学生签到后通过事件触发家长端消息推送，
 * 推送内容仅含到校提示，不含敏感信息。未绑定家长时事件静默丢弃。</p>
 *
 * <p>推送内容脱敏规则：
 * <ul>
 *   <li>仅包含"您的孩子已到校"提示，不含学生姓名、身份证号等</li>
 *   <li>不含签到地点、签到时间等可能泄露行踪的详细信息</li>
 * </ul></p>
 */
@Slf4j
@Service
public class PushNotificationService {

    private final PreRegistrationStore preRegistrationStore;

    public PushNotificationService(PreRegistrationStore preRegistrationStore) {
        this.preRegistrationStore = preRegistrationStore;
    }

    /**
     * 处理学生签到事件，向已绑定家长推送通知。
     *
     * <p>流程：
     * <ol>
     *   <li>根据 studentId 查找已绑定家长手机号</li>
     *   <li>若未绑定，静默丢弃并记录日志</li>
     *   <li>若已绑定，构建脱敏推送内容并发送</li>
     * </ol></p>
     *
     * @param studentId 学生 ID
     * @return true 表示推送成功，false 表示未绑定家长已静默丢弃
     */
    public boolean notifyParent(String studentId) {
        Optional<String> phoneOpt = preRegistrationStore.findPhoneByStudentId(studentId);

        if (phoneOpt.isEmpty()) {
            log.info("学生 {} 未绑定家长，签到通知静默丢弃", studentId);
            return false;
        }

        String phone = phoneOpt.get();
        String content = buildNotificationContent();

        log.info("推送家长通知: studentId={}, phone={}, content={}", studentId, phone, content);
        sendPushNotification(phone, content);
        return true;
    }

    /**
     * 构建推送通知内容（脱敏）。
     *
     * <p>仅包含到校提示，不含任何敏感信息。</p>
     *
     * @return 推送内容
     */
    public String buildNotificationContent() {
        return "您的孩子已到校，请放心。";
    }

    /**
     * 发送推送通知（模拟实现）。
     *
     * <p>生产环境应对接微信小程序订阅消息或短信网关。
     * 当前实现仅记录日志，不实际发送。</p>
     *
     * @param phone   家长手机号
     * @param content 推送内容
     */
    private void sendPushNotification(String phone, String content) {
        // TODO: 对接微信小程序订阅消息 API 或短信网关
        log.info("[模拟推送] phone={}, content={}", phone, content);
    }
}
