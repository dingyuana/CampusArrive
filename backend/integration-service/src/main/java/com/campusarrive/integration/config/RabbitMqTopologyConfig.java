package com.campusarrive.integration.config;

import com.campusarrive.integration.event.EventConstants;
import com.campusarrive.integration.event.EventType;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 拓扑配置。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.1 节队列/交换机拓扑、第 8.3 节死信队列、第 8.4 节重试策略。
 *
 * 拓扑结构：
 * <pre>
 *   student.events (topic exchange)
 *     ├── q.dorm.checkin    ← student.checkin.success
 *     ├── q.card.issue      ← student.checkin.success + student.verified.success
 *     ├── q.parent.notify   ← student.checkin.success
 *     ├── q.edu.activate    ← student.verified.success
 *     ├── q.edu.register    ← student.checkin.completed
 *     ├── q.dorm.allocate   ← student.payment.completed
 *     └── q.flow.complete   ← student.payment.completed
 *
 *   student.events.dlx (direct exchange, 死信)
 *     └── q.dlx             ← 所有业务队列的消费失败/超时消息
 *
 *   student.events.retry (direct exchange, 重试)
 *     ├── q.retry.1 (TTL=10s) → student.events (回原队列)
 *     ├── q.retry.2 (TTL=30s) → student.events (回原队列)
 *     └── q.retry.3 (TTL=60s) → student.events (回原队列)
 * </pre>
 *
 * 所有队列均持久化（durable=true），配置 DLX 与 TTL。
 * 通过 {@code ConditionalOnProperty} 在单元测试中可关闭自动声明。</p>
 */
@Configuration
@ConditionalOnProperty(name = "campusarrive.rabbitmq.topology.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqTopologyConfig {

    // ================================================================
    // 消息转换器
    // ================================================================

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ================================================================
    // 交换机
    // ================================================================

    /** 主 topic 交换机 */
    @Bean
    public TopicExchange studentEventsExchange() {
        return ExchangeBuilder.topicExchange(EventConstants.EXCHANGE_STUDENT_EVENTS)
                .durable(true)
                .build();
    }

    /** 死信交换机（direct 类型，按路由键精确匹配） */
    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(EventConstants.EXCHANGE_DLX)
                .durable(true)
                .build();
    }

    /** 重试交换机（direct 类型） */
    @Bean
    public DirectExchange retryExchange() {
        return ExchangeBuilder.directExchange(EventConstants.EXCHANGE_RETRY)
                .durable(true)
                .build();
    }

    // ================================================================
    // 死信队列
    // ================================================================

    /** 全局死信队列 */
    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(EventConstants.QUEUE_DLX).build();
    }

    /** 死信队列绑定到 DLX */
    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with("#");
    }

    // ================================================================
    // 业务队列（7 个）
    // ================================================================

    /**
     * 构建业务队列的 DLX 参数。
     * 消费失败时经重试交换机路由到阶梯重试队列；
     * 超过重试上限或不可重试异常直接转入死信队列。
     */
    private Map<String, Object> businessQueueArgs() {
        Map<String, Object> args = new HashMap<>();
        // 不可重试异常 → 死信交换机
        args.put("x-dead-letter-exchange", EventConstants.EXCHANGE_DLX);
        // 可重试异常 → 重试交换机（通过代码发送）
        args.put("x-message-ttl", EventConstants.MESSAGE_TTL_MS);
        args.put("x-max-length", EventConstants.QUEUE_MAX_LENGTH);
        return args;
    }

    /** q.dorm.checkin — 宿舍确认队列 */
    @Bean
    public Queue dormCheckinQueue() {
        return QueueBuilder.durable(EventConstants.QUEUE_DORM_CHECKIN)
                .withArguments(businessQueueArgs())
                .build();
    }

    /** q.card.issue — 一卡通制卡队列 */
    @Bean
    public Queue cardIssueQueue() {
        return QueueBuilder.durable(EventConstants.QUEUE_CARD_ISSUE)
                .withArguments(businessQueueArgs())
                .build();
    }

    /** q.parent.notify — 家长通知队列 */
    @Bean
    public Queue parentNotifyQueue() {
        return QueueBuilder.durable(EventConstants.QUEUE_PARENT_NOTIFY)
                .withArguments(businessQueueArgs())
                .build();
    }

    /** q.edu.activate — 教务学籍激活队列 */
    @Bean
    public Queue eduActivateQueue() {
        return QueueBuilder.durable(EventConstants.QUEUE_EDU_ACTIVATE)
                .withArguments(businessQueueArgs())
                .build();
    }

    /** q.edu.register — 教务教籍注册队列 */
    @Bean
    public Queue eduRegisterQueue() {
        return QueueBuilder.durable(EventConstants.QUEUE_EDU_REGISTER)
                .withArguments(businessQueueArgs())
                .build();
    }

    /** q.dorm.allocate — 宿舍正式分配队列 */
    @Bean
    public Queue dormAllocateQueue() {
        return QueueBuilder.durable(EventConstants.QUEUE_DORM_ALLOCATE)
                .withArguments(businessQueueArgs())
                .build();
    }

    /** q.flow.complete — 流程标记完成队列 */
    @Bean
    public Queue flowCompleteQueue() {
        return QueueBuilder.durable(EventConstants.QUEUE_FLOW_COMPLETE)
                .withArguments(businessQueueArgs())
                .build();
    }

    // ================================================================
    // 业务队列绑定到主交换机
    // ================================================================

    @Bean
    public Binding dormCheckinBinding() {
        return BindingBuilder.bind(dormCheckinQueue())
                .to(studentEventsExchange())
                .with(EventType.CHECKIN_SUCCESS.routingKey());
    }

    @Bean
    public Binding cardIssueCheckinBinding() {
        return BindingBuilder.bind(cardIssueQueue())
                .to(studentEventsExchange())
                .with(EventType.CHECKIN_SUCCESS.routingKey());
    }

    @Bean
    public Binding cardIssueVerifiedBinding() {
        return BindingBuilder.bind(cardIssueQueue())
                .to(studentEventsExchange())
                .with(EventType.VERIFIED_SUCCESS.routingKey());
    }

    @Bean
    public Binding parentNotifyBinding() {
        return BindingBuilder.bind(parentNotifyQueue())
                .to(studentEventsExchange())
                .with(EventType.CHECKIN_SUCCESS.routingKey());
    }

    @Bean
    public Binding eduActivateBinding() {
        return BindingBuilder.bind(eduActivateQueue())
                .to(studentEventsExchange())
                .with(EventType.VERIFIED_SUCCESS.routingKey());
    }

    @Bean
    public Binding eduRegisterBinding() {
        return BindingBuilder.bind(eduRegisterQueue())
                .to(studentEventsExchange())
                .with(EventType.CHECKIN_COMPLETED.routingKey());
    }

    @Bean
    public Binding dormAllocateBinding() {
        return BindingBuilder.bind(dormAllocateQueue())
                .to(studentEventsExchange())
                .with(EventType.PAYMENT_COMPLETED.routingKey());
    }

    @Bean
    public Binding flowCompleteBinding() {
        return BindingBuilder.bind(flowCompleteQueue())
                .to(studentEventsExchange())
                .with(EventType.PAYMENT_COMPLETED.routingKey());
    }

    // ================================================================
    // 重试队列（TTL 阶梯退避）
    // ================================================================

    /**
     * 构建重试队列参数。
     * 重试队列的 DLX 指向主交换机，TTL 过期后消息回到主交换机重新路由到原业务队列。
     *
     * @param delaySeconds TTL 延迟（秒）
     */
    private Map<String, Object> retryQueueArgs(long delaySeconds) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EventConstants.EXCHANGE_STUDENT_EVENTS);
        args.put("x-message-ttl", delaySeconds * 1000);
        return args;
    }

    /** q.retry.1 — 第 1 次重试队列，TTL=10s */
    @Bean
    public Queue retryQueue1() {
        return QueueBuilder.durable(EventConstants.QUEUE_RETRY_1)
                .withArguments(retryQueueArgs(EventConstants.RETRY_DELAYS_SECONDS[0]))
                .build();
    }

    /** q.retry.2 — 第 2 次重试队列，TTL=30s */
    @Bean
    public Queue retryQueue2() {
        return QueueBuilder.durable(EventConstants.QUEUE_RETRY_2)
                .withArguments(retryQueueArgs(EventConstants.RETRY_DELAYS_SECONDS[1]))
                .build();
    }

    /** q.retry.3 — 第 3 次重试队列，TTL=60s */
    @Bean
    public Queue retryQueue3() {
        return QueueBuilder.durable(EventConstants.QUEUE_RETRY_3)
                .withArguments(retryQueueArgs(EventConstants.RETRY_DELAYS_SECONDS[2]))
                .build();
    }

    /** 重试队列 1 绑定到重试交换机 */
    @Bean
    public Binding retry1Binding() {
        return BindingBuilder.bind(retryQueue1())
                .to(retryExchange())
                .with("retry.1");
    }

    /** 重试队列 2 绑定到重试交换机 */
    @Bean
    public Binding retry2Binding() {
        return BindingBuilder.bind(retryQueue2())
                .to(retryExchange())
                .with("retry.2");
    }

    /** 重试队列 3 绑定到重试交换机 */
    @Bean
    public Binding retry3Binding() {
        return BindingBuilder.bind(retryQueue3())
                .to(retryExchange())
                .with("retry.3");
    }
}
