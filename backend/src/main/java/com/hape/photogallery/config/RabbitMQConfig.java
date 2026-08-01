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

    /** 处理队列（持久、含 DLX） */
    @Bean
    Queue processingQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY);
        return QueueBuilder.durable(QUEUE).withArguments(args).build();
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

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ_ROUTING_KEY);
    }

    /** JSON 序列化 RabbitTemplate，仅允许白名单类型反序列化 */
    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(ProcessingMessage.class)
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);

        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(new Jackson2JsonMessageConverter(mapper));
        return template;
    }

    /** Consumer 容器工厂：MANUAL ack + 2-4 并发 + MDC 传播 */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory factory) {
        SimpleRabbitListenerContainerFactory containerFactory = new SimpleRabbitListenerContainerFactory();
        containerFactory.setConnectionFactory(factory);
        containerFactory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        containerFactory.setPrefetchCount(1);
        containerFactory.setConcurrentConsumers(2);
        containerFactory.setMaxConcurrentConsumers(4);
        containerFactory.setDefaultRequeueRejected(false);
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
