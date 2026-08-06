package com.hape.photogallery.config;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.hape.photogallery.messaging.ProcessingMessage;

@Configuration
@ConditionalOnProperty(name = "photo.processing.type", havingValue = "rabbitmq")
public class RabbitMQConfig {

    public static final String EXCHANGE = "pg.photo.ex";
    public static final String DLX_EXCHANGE = "pg.photo.dlx";
    public static final String QUEUE = "pg.photo.processing";
    public static final String DLQ = "pg.photo.processing.dlq";
    public static final String ROUTING_KEY = "photo.processing";
    public static final String DLQ_ROUTING_KEY = "photo.processing.dlq";
    /** TTL 重试队列——consumer 失败时显式重投到此队列，TTL 到期死信回主队列重试；
     *  重试次数由自定义 header x-retry-count 记录（随显式重投持久化，与 broker 版本无关——
     *  实测 RabbitMQ 4.x 死信时 x-death 被重置而非合并递增，不可依赖）。
     *  注意：TTL 是队列参数，改动需先删旧队列（rabbitmqctl delete_queue pg.photo.processing.retry），
     *  否则声明 406 PRECONDITION_FAILED 启动失败 */
    public static final String RETRY_QUEUE = "pg.photo.processing.retry";
    public static final String RETRY_ROUTING_KEY = "photo.processing.retry";
    /** 10s → 30s：2 核慢机器上瞬时抖动（DB 抖动/磁盘满/连接池耗尽）常超过 10s，
     *  过窄窗口会把在途照片批量判死 FAILED（P0 修复，配合 DlqRequeuer 自动恢复） */
    public static final long RETRY_TTL_MS = 30_000;

    /** 处理队列（持久、含 DLX） */
    @Bean
    Queue processingQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY);
        return QueueBuilder.durable(QUEUE).withArguments(args).build();
    }

    /** 重试队列（无消费者）：TTL 到期死信回主交换机，经主绑定回到处理队列（等待室语义） */
    @Bean
    Queue retryQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", RETRY_TTL_MS);
        args.put("x-dead-letter-exchange", EXCHANGE);
        args.put("x-dead-letter-routing-key", ROUTING_KEY);
        return QueueBuilder.durable(RETRY_QUEUE).withArguments(args).build();
    }

    /** 死信队列 */
    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    /** 主交换机 */
    @Bean
    DirectExchange processingExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /** 死信交换机 */
    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    Binding processingBinding() {
        return BindingBuilder.bind(processingQueue()).to(processingExchange()).with(ROUTING_KEY);
    }

    /** 主交换机 → 重试队列（consumer 失败时显式重投的路由） */
    @Bean
    Binding retryBinding() {
        return BindingBuilder.bind(retryQueue()).to(processingExchange()).with(RETRY_ROUTING_KEY);
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ_ROUTING_KEY);
    }

    /**
     * 生产/消费共用的 JSON 消息转换器，仅允许白名单类型反序列化。
     * 发布侧（RabbitTemplate）与消费侧（监听容器工厂）必须用同一个转换器——
     * 否则发布为 application/json，消费端默认 SimpleMessageConverter 只返回 byte[]，
     * POJO 参数解析失败 → MessageConversionException（fatal）→ 直接进 DLQ，
     * 上传永久卡 PROCESSING（P0 修复，曾缺 setMessageConverter）。
     */
    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(ProcessingMessage.class)
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        return new Jackson2JsonMessageConverter(mapper);
    }

    /** JSON 序列化 RabbitTemplate（发布侧） */
    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory factory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);
        return template;
    }

    /** Consumer 容器工厂：MANUAL ack + 2-4 并发 + MDC 传播 + JSON 转换器（与发布侧一致） */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory factory,
                                                                        Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory containerFactory = new SimpleRabbitListenerContainerFactory();
        containerFactory.setConnectionFactory(factory);
        containerFactory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        containerFactory.setPrefetchCount(1);
        containerFactory.setConcurrentConsumers(2);
        containerFactory.setMaxConcurrentConsumers(4);
        containerFactory.setDefaultRequeueRejected(false);
        containerFactory.setMessageConverter(converter);
        containerFactory.setTaskExecutor(mdcAwareExecutor());
        return containerFactory;
    }

    /** MDC 传播线程池 */
    private ThreadPoolTaskExecutor mdcAwareExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("rmq-consume-");
        executor.setTaskDecorator(task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) MDC.setContextMap(context);
                try { task.run(); } finally { MDC.clear(); }
            };
        });
        executor.initialize();
        return executor;
    }
}
