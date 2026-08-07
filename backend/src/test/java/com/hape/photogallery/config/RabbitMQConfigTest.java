package com.hape.photogallery.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;

import com.hape.photogallery.messaging.ProcessingMessage;

class RabbitMQConfigTest {

    private RabbitMQConfig config;
    private Jackson2JsonMessageConverter converter;

    @BeforeEach
    void setUp() {
        // 直接实例化配置类，走真实 @Bean 方法（无 Spring 上下文）
        config = new RabbitMQConfig();
        converter = config.jackson2JsonMessageConverter();
    }

    @Test
    void constants_shouldBeCorrect() {
        assertThat(RabbitMQConfig.EXCHANGE).isEqualTo("pg.photo.ex");
        assertThat(RabbitMQConfig.DLX_EXCHANGE).isEqualTo("pg.photo.dlx");
        assertThat(RabbitMQConfig.QUEUE).isEqualTo("pg.photo.processing");
        assertThat(RabbitMQConfig.DLQ).isEqualTo("pg.photo.processing.dlq");
        assertThat(RabbitMQConfig.ROUTING_KEY).isEqualTo("photo.processing");
        assertThat(RabbitMQConfig.DLQ_ROUTING_KEY).isEqualTo("photo.processing.dlq");
        assertThat(RabbitMQConfig.RETRY_QUEUE).isEqualTo("pg.photo.processing.retry");
        assertThat(RabbitMQConfig.RETRY_ROUTING_KEY).isEqualTo("photo.processing.retry");
        assertThat(RabbitMQConfig.RETRY_TTL_MS).isEqualTo(30_000L); // 10s→30s：2 核慢机器重试窗口（P0）
    }

    /**
     * P0 回归：发布侧（Jackson JSON）→ 消费侧（容器工厂同款转换器）必须能还原 POJO。
     * 曾缺 setMessageConverter：消费端默认 SimpleMessageConverter 对 application/json
     * 只返回 byte[]，POJO 参数解析失败 → MessageConversionException（fatal）→ DLQ，
     * 生产上传永久卡 PROCESSING。此用例直接覆盖「序列化 → 解析」往返。
     */
    @Test
    void processingMessage_jsonRoundTrip_viaContainerFactoryConverter() {
        ProcessingMessage original = new ProcessingMessage(42L, "2026/08", "uuid_IMG_1.jpg", "水印");

        Message message = converter.toMessage(original, new MessageProperties());
        assertThat(message.getMessageProperties().getContentType())
                .as("发布 content-type 必须是 JSON（SimpleMessageConverter 无法处理该类型）")
                .isEqualTo("application/json");

        Object parsed = converter.fromMessage(message);
        assertThat(parsed).isInstanceOf(ProcessingMessage.class);
        ProcessingMessage msg = (ProcessingMessage) parsed;
        assertThat(msg.getPhotoId()).isEqualTo(42L);
        assertThat(msg.getDateDir()).isEqualTo("2026/08");
        assertThat(msg.getBaseName()).isEqualTo("uuid_IMG_1.jpg");
        assertThat(msg.getWatermark()).isEqualTo("水印");
    }

    /** 回归对照：默认 SimpleMessageConverter 对 application/json 只能还原 byte[]——
     *  证明若容器工厂不设置转换器（P0 缺陷）POJO 参数必然解析失败 */
    @Test
    void defaultSimpleConverter_cannotResolveJsonToPojo() {
        ProcessingMessage original = new ProcessingMessage(1L, "2026/08", "b.jpg", null);
        Message message = converter.toMessage(original, new MessageProperties());

        Object parsed = new SimpleMessageConverter().fromMessage(message);
        assertThat(parsed).isNotInstanceOf(ProcessingMessage.class);
    }

    /**
     * P0 装配回归：监听容器工厂必须装配与发布侧相同的 JSON 转换器。
     * 此前缺陷正是「template 配了 converter 而容器工厂没配」——往返测试
     * （toMessage→fromMessage）绕过工厂测不出装配缺失，这里直接断言装配。
     * setConnectionFactory/setMessageConverter 为纯 setter，不触发真实连接。
     */
    @Test
    void containerFactory_shouldUseSameJsonConverterAsTemplate() throws Exception {
        org.springframework.amqp.rabbit.connection.ConnectionFactory cf =
                org.mockito.Mockito.mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class);

        var factory = config.rabbitListenerContainerFactory(cf, converter);
        // SimpleRabbitListenerContainerFactory 无 converter getter——反射读父类私有字段
        java.lang.reflect.Field f = org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory.class
                .getDeclaredField("messageConverter");
        f.setAccessible(true);
        assertThat(f.get(factory)).as("容器工厂缺 setMessageConverter（P0 复发）").isSameAs(converter);
    }
}
