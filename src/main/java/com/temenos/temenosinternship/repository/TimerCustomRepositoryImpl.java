package com.temenos.temenosinternship.repository;

import com.temenos.temenosinternship.domain.TimerEntity;
import com.temenos.temenosinternship.domain.TimerStatus;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Custom repository implementation for atomic timer operations.
 */
@Repository
public class TimerCustomRepositoryImpl implements TimerCustomRepository {

    private final DatabaseClient databaseClient;

    /**
     * Creates a custom timer repository implementation.
     *
     * @param databaseClient reactive database client
     */
    public TimerCustomRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * Returns all persisted timers.
     *
     * @return all timers
     */
    @Override
    public Flux<TimerEntity> findAllTimers() {
        return databaseClient.sql("""
            SELECT timer_id, created, delay, status, attempts, updated_at, loader_claimed_at
            FROM timer
            ORDER BY created DESC
            """)
            .map((row, metadata) -> mapTimer(row))
            .all();
    }

    /**
     * Finds a timer by identifier.
     *
     * @param timerId timer identifier
     * @return matching timer or empty result
     */
    @Override
    public Mono<TimerEntity> findTimerById(UUID timerId) {
        return databaseClient.sql("""
            SELECT timer_id, created, delay, status, attempts, updated_at, loader_claimed_at
            FROM timer
            WHERE timer_id = $1
            """)
            .bind(0, timerId)
            .map((row, metadata) -> mapTimer(row))
            .one();
    }

    /**
     * Inserts a new timer row.
     *
     * @param timerId timer identifier
     * @param created creation timestamp
     * @param delay delay in seconds
     * @param status initial status
     * @param attempts number of attempts
     * @param updatedAt update timestamp
     * @return number of inserted rows
     */
    @Override
    public Mono<Integer> insertTimer(UUID timerId, long created, int delay, TimerStatus status, int attempts, long updatedAt) {
        return databaseClient.sql("""
            INSERT INTO timer (timer_id, created, delay, status, attempts, updated_at)
            VALUES ($1, $2, $3, $4, $5, $6)
            """)
            .bind(0, timerId)
            .bind(1, created)
            .bind(2, delay)
            .bind(3, status.name())
            .bind(4, attempts)
            .bind(5, updatedAt)
            .fetch()
            .rowsUpdated()
            .map(Math::toIntExact);
    }

    /**
     * Claims long timers that are now near their threshold.
     *
     * @param now current time in milliseconds
     * @param nearLimit threshold timestamp in milliseconds
     * @param limit batch size
     * @return claimed timers
     */
    @Override
    public Flux<TimerEntity> claimLongTimers(long now, long nearLimit, int limit) {
        return databaseClient.sql("""
            WITH claimed AS (
                SELECT timer_id
                FROM timer
                WHERE status = 'PENDING'
                  AND (created + delay * 1000) <= $1
                ORDER BY created
                LIMIT $2
                FOR UPDATE SKIP LOCKED
            )
            UPDATE timer t
            SET status = 'LOADING',
                loader_claimed_at = $3,
                updated_at = $3
            FROM claimed
            WHERE t.timer_id = claimed.timer_id
            RETURNING t.timer_id, t.created, t.delay, t.status, t.attempts, t.updated_at, t.loader_claimed_at
            """)
            .bind(0, nearLimit)
            .bind(1, limit)
            .bind(2, now)
            .map((row, metadata) -> mapTimer(row))
            .all();
    }

    /**
     * Marks a loading timer as scheduled.
     *
     * @param timerId timer identifier
     * @param updatedAt update timestamp
     * @return number of updated rows
     */
    @Override
    public Mono<Integer> markScheduledFromLoading(UUID timerId, long updatedAt) {
        return updateStatus(timerId, "LOADING", "SCHEDULED", updatedAt);
    }

    /**
     * Marks a scheduled timer as ready.
     *
     * @param timerId timer identifier
     * @param updatedAt update timestamp
     * @return number of updated rows
     */
    @Override
    public Mono<Integer> markReady(UUID timerId, long updatedAt) {
        return updateStatus(timerId, "SCHEDULED", "READY", updatedAt);
    }

    /**
     * Marks a ready timer as processing.
     *
     * @param timerId timer identifier
     * @param updatedAt update timestamp
     * @return number of updated rows
     */
    @Override
    public Mono<Integer> markProcessing(UUID timerId, long updatedAt) {
        return databaseClient.sql("""
            UPDATE timer
            SET status = 'PROCESSING',
                updated_at = $2
            WHERE timer_id = $1
              AND status = 'READY'
            """)
            .bind(0, timerId)
            .bind(1, updatedAt)
            .fetch()
            .rowsUpdated()
            .map(Math::toIntExact);
    }

    /**
     * Marks a processing timer as completed.
     *
     * @param timerId timer identifier
     * @param updatedAt update timestamp
     * @return number of updated rows
     */
    @Override
    public Mono<Integer> markCompleted(UUID timerId, long updatedAt) {
        return updateStatus(timerId, "PROCESSING", "COMPLETED", updatedAt);
    }

    /**
     * Increments attempts and reschedules a timer.
     *
     * @param timerId timer identifier
     * @param updatedAt update timestamp
     * @return number of updated rows
     */
    @Override
    public Mono<Integer> markRetryScheduled(UUID timerId, long updatedAt) {
        return databaseClient.sql("""
            UPDATE timer
            SET attempts = attempts + 1,
                status = 'SCHEDULED',
                updated_at = $2
            WHERE timer_id = $1
              AND status = 'PROCESSING'
            """)
            .bind(0, timerId)
            .bind(1, updatedAt)
            .fetch()
            .rowsUpdated()
            .map(Math::toIntExact);
    }

    /**
     * Increments attempts and marks a timer as failed.
     *
     * @param timerId timer identifier
     * @param updatedAt update timestamp
     * @return number of updated rows
     */
    @Override
    public Mono<Integer> markFailed(UUID timerId, long updatedAt) {
        return databaseClient.sql("""
            UPDATE timer
            SET attempts = attempts + 1,
                status = 'FAILED',
                updated_at = $2
            WHERE timer_id = $1
              AND status = 'PROCESSING'
            """)
            .bind(0, timerId)
            .bind(1, updatedAt)
            .fetch()
            .rowsUpdated()
            .map(Math::toIntExact);
    }

    /**
     * Recovers stale loading timers.
     *
     * @param staleBefore stale threshold timestamp
     * @param updatedAt update timestamp
     * @return recovered timers
     */
    @Override
    public Flux<TimerEntity> recoverStaleLoadingTimers(long staleBefore, long updatedAt) {
        return databaseClient.sql("""
            UPDATE timer
            SET status = 'PENDING',
                loader_claimed_at = NULL,
                updated_at = $2
            WHERE status = 'LOADING'
              AND loader_claimed_at < $1
            RETURNING timer_id, created, delay, status, attempts, updated_at, loader_claimed_at
            """)
            .bind(0, staleBefore)
            .bind(1, updatedAt)
            .map((row, metadata) -> mapTimer(row))
            .all();
    }

    private Mono<Integer> updateStatus(UUID timerId, String currentStatus, String nextStatus, long updatedAt) {
        return databaseClient.sql("""
            UPDATE timer
            SET status = $3,
                loader_claimed_at = NULL,
                updated_at = $4
            WHERE timer_id = $1
              AND status = $2
            """)
            .bind(0, timerId)
            .bind(1, currentStatus)
            .bind(2, nextStatus)
            .bind(3, updatedAt)
            .fetch()
            .rowsUpdated()
            .map(Math::toIntExact);
    }

    private TimerEntity mapTimer(io.r2dbc.spi.Readable row) {
        TimerEntity entity = new TimerEntity();
        entity.setTimerId(row.get("timer_id", UUID.class));
        entity.setCreated(row.get("created", Long.class));
        entity.setDelay(row.get("delay", Integer.class));
        entity.setStatus(TimerStatus.valueOf(row.get("status", String.class)));
        entity.setAttempts(row.get("attempts", Integer.class));
        entity.setUpdatedAt(row.get("updated_at", Long.class));
        entity.setLoaderClaimedAt(row.get("loader_claimed_at", Long.class));
        return entity;
    }
}
