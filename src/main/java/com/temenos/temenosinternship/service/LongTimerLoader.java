package com.temenos.temenosinternship.service;

import com.temenos.temenosinternship.config.TimerProperties;
import com.temenos.temenosinternship.domain.TimerEntity;
import com.temenos.temenosinternship.repository.TimerRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Loads long timers from PostgreSQL into the delayed queue when they become near-term.
 */
@Service
public class LongTimerLoader {

    private static final Logger log = LoggerFactory.getLogger(LongTimerLoader.class);
    private static final Duration LOAD_INTERVAL = Duration.ofSeconds(5);

    private final TimerRepository timerRepository;
    private final TimerProperties timerProperties;
    private final DelayedQueueService delayedQueueService;

    /**
     * Creates a long timer loader.
     *
     * @param timerRepository timer repository
     * @param timerProperties timer configuration properties
     * @param delayedQueueService delayed queue service
     */
    public LongTimerLoader(
        TimerRepository timerRepository,
        TimerProperties timerProperties,
        DelayedQueueService delayedQueueService
    ) {
        this.timerRepository = timerRepository;
        this.timerProperties = timerProperties;
        this.delayedQueueService = delayedQueueService;
    }

    /**
     * Starts the long timer loading and stale-claim recovery loops after the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Flux.interval(LOAD_INTERVAL)
            .flatMap(ignored -> claimAndSchedule())
            .onErrorContinue((error, value) -> log.error("Long timer loader loop failed", error))
            .subscribe();

        Flux.interval(LOAD_INTERVAL)
            .flatMap(ignored -> recoverStaleClaims())
            .onErrorContinue((error, value) -> log.error("Long timer recovery loop failed", error))
            .subscribe();
    }

    /**
     * Claims long timers that have entered the near-term threshold and schedules them.
     *
     * @return completion signal
     */
    public Mono<Void> claimAndSchedule() {
        long now = System.currentTimeMillis();
        long nearLimit = now + timerProperties.getLongThresholdSeconds() * 1000L;

        return timerRepository.claimLongTimers(now, nearLimit, timerProperties.getLoaderBatchSize())
            .concatMap(this::scheduleClaimedTimer)
            .then();
    }

    /**
     * Returns stale loader claims back to pending status.
     *
     * @return completion signal
     */
    public Mono<Void> recoverStaleClaims() {
        long now = System.currentTimeMillis();
        long staleBefore = now - timerProperties.getLoaderClaimTimeoutMs();

        return timerRepository.recoverStaleLoadingTimers(staleBefore, now)
            .doOnNext(timer -> log.debug("Recovered stale long timer {}", timer.getTimerId()))
            .then();
    }

    private Mono<Void> scheduleClaimedTimer(TimerEntity timer) {
        return delayedQueueService.schedule(timer.getTimerId(), timer.getCreated(), timer.getDelay())
            .then(timerRepository.markScheduledFromLoading(timer.getTimerId(), System.currentTimeMillis()))
            .doOnSuccess(ignored -> log.debug("Loaded long timer {}", timer.getTimerId()))
            .then();
    }
}
