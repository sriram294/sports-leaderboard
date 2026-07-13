package com.org.playboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} so push notifications dispatched from
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} run off the request
 * thread — a slow or failing FCM call never blocks or breaks a match write.
 */
@Configuration
@EnableAsync
public class AsyncConfig {}
