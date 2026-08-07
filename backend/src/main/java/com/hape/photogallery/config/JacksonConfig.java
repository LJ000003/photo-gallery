package com.hape.photogallery.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;

/**
 * Jackson + Hibernate 集成：手动注册 Hibernate6Module（自动配置在部分场景不生效——
 * 实测回收站列表序列化 @ManyToOne 懒加载代理仍抛 HttpMessageConversionException 500）。
 * 未初始化代理序列化为 null（不触发懒加载——DTO 已在事务内完成初始化，此处仅兜底防炸）。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer hibernateModuleCustomizer() {
        return builder -> builder.modulesToInstall(new Hibernate6Module());
    }
}
