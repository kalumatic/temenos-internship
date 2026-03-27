package com.temenos.temenosinternship.repository;

import com.temenos.temenosinternship.domain.TimerEntity;
import com.temenos.temenosinternship.domain.TimerStatus;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Custom reactive persistence operations for timers.
 */
public interface TimerCustomRepository {

    /**
     * Returns all persisted timers.
     *
     * @return all timers
     */
    Flux<TimerEntity> findAllTimers();

    /**
     * Finds a timer by identifier.
     *
     * @param timerId timer identifier
     * @return matching timer or empty result
     */
    Mono<TimerEntity> findTimerById(UUID timerId);

    /**
     * Inserts a new timer row.
     *
     * @param timerId timer identifier
     * @param created creation timestamp
     * @param delay delay in seconds
     * @param callbackUrl callback URL
     * @param csrfToken CSRF token
     * @param status initial timer status
     * @param attempts number of attempts
     * @param updatedAt last update timestamp
     * @return number of inserted rows
     */
    Mono<Integer> insertTimer(
        UUID timerId,
        long created,
        int delay,
        String callbackUrl,
        String csrfToken,
        TimerStatus status,
        int attempts,
        long updatedAt
    );

    /**
     * Claims long timers that are now near their scheduling threshold.
     *
     * @param now current time in milliseconds
     * @param nearLimit upper scheduling threshold in milliseconds
     * @param limit batch size
     * @return claimed timers
     */
    Flux<TimerEntity> claimLongTimers(long now, long nearLimit, int limit);

    /**
     * Marks a loading timer as scheduled after it is successfully enqueued.
     *
     * @param timerId timer identifier
     * @param updatedAt last update timestamp
     * @return number of updated rows
     */
    Mono<Integer> markScheduledFromLoading(UUID timerId, long updatedAt);

    /**
     * Marks a timer as ready after it has been forwarded to the due stream.
     *
     * @param timerId timer identifier
     * @param updatedAt last update timestamp
     * @return number of updated rows
     */
    Mono<Integer> markReady(UUID timerId, long updatedAt);

    /**
     * Claims a ready timer for processing.
     *
     * @param timerId timer identifier
     * @param updatedAt last update timestamp
     * @return number of updated rows
     */
    Mono<Integer> markProcessing(UUID timerId, long updatedAt);

    /**
     * Marks a timer as completed.
     *
     * @param timerId timer identifier
     * @param updatedAt last update timestamp
     * @return number of updated rows
     */
    Mono<Integer> markCompleted(UUID timerId, long updatedAt);

    /**
     * Increments attempts and reschedules a timer.
     *
     * @param timerId timer identifier
     * @param updatedAt last update timestamp
     * @return number of updated rows
     */
    Mono<Integer> markRetryScheduled(UUID timerId, long updatedAt);

    /**
     * Increments attempts and marks a timer as failed.
     *
     * @param timerId timer identifier
     * @param updatedAt last update timestamp
     * @return number of updated rows
     */
    Mono<Integer> markFailed(UUID timerId, long updatedAt);

    /**
     * Recovers stale loader claims.
     *
     * @param staleBefore timestamp before which claims are stale
     * @param updatedAt last update timestamp
     * @return recovered timers
     */
    Flux<TimerEntity> recoverStaleLoadingTimers(long staleBefore, long updatedAt);
}
