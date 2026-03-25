package com.temenos.temenosinternship.service;

import com.temenos.temenosinternship.config.TimerProperties;
import org.springframework.stereotype.Service;

/**
 * Provides reusable timer scheduling rules.
 */
@Service
public class TimerService {

    private final TimerProperties timerProperties;

    /**
     * Creates a timer service.
     *
     * @param timerProperties timer configuration properties
     */
    public TimerService(TimerProperties timerProperties) {
        this.timerProperties = timerProperties;
    }

    /**
     * Calculates the remaining delay in milliseconds for a timer.
     *
     * @param created creation timestamp in milliseconds
     * @param delaySeconds delay in seconds
     * @param now current timestamp in milliseconds
     * @return remaining delay in milliseconds, clamped to zero
     */
    public long calculateTimeoutMillis(long created, int delaySeconds, long now) {
        long delayMillis = delaySeconds * 1000L;
        return Math.max(0L, delayMillis - (now - created));
    }

    /**
     * Determines whether a timer should be treated as near-term.
     *
     * @param created creation timestamp in milliseconds
     * @param delaySeconds delay in seconds
     * @param now current timestamp in milliseconds
     * @return {@code true} if the timer is near-term
     */
    public boolean isNear(long created, int delaySeconds, long now) {
        return calculateTimeoutMillis(created, delaySeconds, now)
            <= timerProperties.getLongThresholdSeconds() * 1000L;
    }

    /**
     * Calculates a retry delay in milliseconds based on the next attempt number.
     *
     * @param nextAttempt next attempt number
     * @return retry delay in milliseconds
     */
    public long calculateRetryDelayMillis(int nextAttempt) {
        return timerProperties.getRetryBaseDelaySeconds() * 1000L * nextAttempt;
    }

    /**
     * Determines whether the timer should be retried.
     *
     * @param nextAttempt next attempt number
     * @return {@code true} if the timer should be retried
     */
    public boolean shouldRetry(int nextAttempt) {
        return nextAttempt < timerProperties.getMaxAttempts();
    }
}
