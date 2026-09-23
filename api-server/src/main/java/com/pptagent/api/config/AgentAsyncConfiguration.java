package com.pptagent.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AgentAsyncConfiguration {

    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor(
            @Value("${ppt.agent.generation.worker-count:4}") int workerCount,
            @Value("${ppt.agent.generation.queue-capacity:100}") int queueCapacity
    ) {
        if (workerCount < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("Generation worker count and queue capacity must be positive");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerCount);
        executor.setMaxPoolSize(workerCount);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("agent-generation-");
        // Keep request/scheduler threads out of model execution. A rejected
        // task remains QUEUED in the database and is picked up by the durable
        // dispatcher on its next poll.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
