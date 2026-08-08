package com.campusarrive.parent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 家长端 RabbitMQ 配置。
 *
 * <p>规格来源：FR-03-06 / FR-04-05 —
 * parent-service 独立声明队列 q.parent.notify.push，绑定到 student.events 交换机，
 * 监听 student.checkin.success 路由键。Topic 交换机将消息扇出到所有绑定队列，
 * 因此 integration-service 和 parent-service 各自独立消费，互不干扰。</p>
 *
 * <p>条件开关：campusarrive.rabbitmq.topology.enabled=true 时生效，
 * 测试环境可通过设置 false 关闭自动声明。</p>
 */
@Configuration
@ConditionalOnProperty(name = "campusarrive.rabbitmq.topology.enabled",
        havingValue = "true", matchIfMissing = true)
public class ParentRabbitMqConfig {

    /** 交换机名称（与 integration-service 一致）。 */
    public static final String EXCHANGE_STUDENT_EVENTS = "student.events";

    /** 家长推送独立队列名称。 */
    public static final String QUEUE_PARENT_NOTIFY_PUSH = "q.parent.notify.push";

    /** 签到成功路由键。 */
    public static final String ROUTING_KEY_CHECKIN_SUCCESS = "student.checkin.success";

    /**
     * 声明交换机（如果不存在则创建）。
     * durable=true 确保 broker 重启后交换机不丢失。
     */
    @Bean
    public TopicExchange studentEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_STUDENT_EVENTS)
                .durable(true)
                .build();
    }

    /**
     * 声明家长推送队列。
     * durable=true 确保消息持久化。
     */
    @Bean
    public Queue parentNotifyPushQueue() {
        return QueueBuilder.durable(QUEUE_PARENT_NOTIFY_PUSH).build();
    }

    /**
     * 绑定队列到交换机，监听签到成功事件。
     */
    @Bean
    public Binding parentNotifyPushBinding() {
        return BindingBuilder.bind(parentNotifyPushQueue())
                .to(studentEventsExchange())
                .with(ROUTING_KEY_CHECKIN_SUCCESS);
    }

    /**
     * JSON 消息转换器。
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
