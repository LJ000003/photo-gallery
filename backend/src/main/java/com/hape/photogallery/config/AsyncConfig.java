package com.hape.photogallery.config;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("imageTaskExecutor")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "photo.processing.type", havingValue = "async", matchIfMissing = true)
    public ThreadPoolTaskExecutor imageTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("img-proc-");
        // 拒绝策略改 Discard——队列满时丢弃任务而非在请求线程同步跑处理链
        // （CallerRunsPolicy 曾使高并发下 afterCommit 在请求线程执行整条处理链，上传从毫秒变秒级，
        // 且处理异常穿透 afterCommit 抛给请求 → 500 但照片已入库）。
        // 被丢弃的消息由 PhotoService 5 分钟定时重扫 PROCESSING 照片补发（仅 async 路径；
        // rabbitmq 模式消息入队列不丢弃，重扫只起兜底作用）。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 传播 MDC（traceId）到异步工作线程
        executor.setTaskDecorator(task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });

        executor.initialize();
        return executor;
    }
}
