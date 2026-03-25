package com.temenos.temenosinternship.domain;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Reactive persistence model for timer data stored in PostgreSQL.
 */
@Table("timer")
public class TimerEntity {

    @Id
    @Column("timer_id")
    private UUID timerId;

    @Column("created")
    private long created;

    @Column("delay")
    private int delay;

    @Column("status")
    private TimerStatus status;

    @Column("attempts")
    private int attempts;

    @Column("updated_at")
    private long updatedAt;

    @Column("loader_claimed_at")
    private Long loaderClaimedAt;

    /**
     * Returns the timer identifier.
     *
     * @return timer identifier
     */
    public UUID getTimerId() {
        return timerId;
    }

    /**
     * Sets the timer identifier.
     *
     * @param timerId timer identifier
     */
    public void setTimerId(UUID timerId) {
        this.timerId = timerId;
    }

    /**
     * Returns the creation timestamp in milliseconds.
     *
     * @return creation timestamp
     */
    public long getCreated() {
        return created;
    }

    /**
     * Sets the creation timestamp in milliseconds.
     *
     * @param created creation timestamp
     */
    public void setCreated(long created) {
        this.created = created;
    }

    /**
     * Returns the delay in seconds.
     *
     * @return delay in seconds
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the delay in seconds.
     *
     * @param delay delay in seconds
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * Returns the current timer status.
     *
     * @return timer status
     */
    public TimerStatus getStatus() {
        return status;
    }

    /**
     * Sets the current timer status.
     *
     * @param status timer status
     */
    public void setStatus(TimerStatus status) {
        this.status = status;
    }

    /**
     * Returns the number of processing attempts.
     *
     * @return number of processing attempts
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * Sets the number of processing attempts.
     *
     * @param attempts number of processing attempts
     */
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    /**
     * Returns the last update timestamp in milliseconds.
     *
     * @return last update timestamp
     */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last update timestamp in milliseconds.
     *
     * @param updatedAt last update timestamp
     */
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Returns the loader claim timestamp in milliseconds.
     *
     * @return loader claim timestamp
     */
    public Long getLoaderClaimedAt() {
        return loaderClaimedAt;
    }

    /**
     * Sets the loader claim timestamp in milliseconds.
     *
     * @param loaderClaimedAt loader claim timestamp
     */
    public void setLoaderClaimedAt(Long loaderClaimedAt) {
        this.loaderClaimedAt = loaderClaimedAt;
    }
}
