package com.hape.photogallery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * 分页参数钳制：pageSize 上限 100（默认 2000）。生产容器 -Xmx448m 下 size=100000
 * 一次请求即可拉 10 万行 DTO 撑爆堆——超限请求被静默钳制到 100，不报错不影响翻页语义。
 * 程序内 PageRequest.of(...) 不经过 web 解析器，不受钳制；@PageableDefault(size=50)
 * （timeline）默认值 50 < 100 也不受影响。
 */
@Configuration
public class PageableConfig {

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return (PageableHandlerMethodArgumentResolver r) -> r.setMaxPageSize(100);
    }
}
