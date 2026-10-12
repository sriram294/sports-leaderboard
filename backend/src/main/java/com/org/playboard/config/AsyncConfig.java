package com.org.playboard.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounds best-effort notification work so a burst of slow FCM sends cannot create an
 * unbounded number of threads or block the transaction that published the event.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(name = NOTIFICATION_EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor(
            MeterRegistry meterRegistry,
            @Value("${playboard.notifications.executor.core-pool-size:2}") int corePoolSize,
            @Value("${playboard.notifications.executor.max-pool-size:4}") int maxPoolSize,
            @Value("${playboard.notifications.executor.queue-capacity:100}") int queueCapacity,
            @Value("${playboard.notifications.executor.keep-alive-seconds:30}") int keepAliveSeconds) {
        Counter discarded = Counter.builder("playboard.notifications.discarded")
                .description("Best-effort notifications discarded because the executor queue was full")
                .register(meterRegistry);
        Counter rejected = Counter.builder("playboard.notifications.rejected")
                .description("Best-effort notifications rejected because the executor was unavailable")
                .register(meterRegistry);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("notification-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new DiscardOldestWithMetricsPolicy(discarded, rejected));
        executor.initialize();
        ExecutorServiceMetrics.monitor(meterRegistry, executor.getThreadPoolExecutor(), "playboard.notification.executor");
        return executor;
    }

    /** Discards the oldest queued notification under load; mutations must never wait on push work. */
    private static final class DiscardOldestWithMetricsPolicy implements RejectedExecutionHandler {
        private final Counter discarded;
        private final Counter rejected;

        private DiscardOldestWithMetricsPolicy(Counter discarded, Counter rejected) {
            this.discarded = discarded;
            this.rejected = rejected;
        }

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                rejected.increment();
                return;
            }
            Runnable oldest = executor.getQueue().poll();
            if (oldest == null) {
                rejected.increment();
                return;
            }
            discarded.increment();
            try {
                executor.execute(task);
            } catch (RejectedExecutionException exception) {
                rejected.increment();
            }
        }
    }
}
