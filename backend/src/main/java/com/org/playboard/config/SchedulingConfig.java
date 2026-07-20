package com.org.playboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} support, used by the end-of-session rank-change job.
 *
 * <p>Jobs must be safe to run concurrently on more than one instance: Railway can have
 * two containers alive across a redeploy. Safety comes from claiming a row in
 * {@code notification_log} before sending rather than from any locking scheme, which
 * keeps this dependency-free (no ShedLock).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
