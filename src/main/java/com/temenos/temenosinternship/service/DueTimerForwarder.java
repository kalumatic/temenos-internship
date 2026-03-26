package com.temenos.temenosinternship.service;

import com.temenos.temenosinternship.domain.DueTimerMessage;
import com.temenos.temenosinternship.repository.TimerRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Forwards due timers from the Redisson blocking queue to the Redis due timer stream.
 */
@Service
public class DueTimerForwarder {

    private static final Logger log = LoggerFactory.getLogger(DueTimerForwarder.class);

    private final DelayedQueueService delayedQueueService;
    private final DueTimerStreamPublisher dueTimerStreamPublisher;
    private final TimerRepository timerRepository;

    /**
     * Creates a due timer forwarder.
     *
     * @param delayedQueueService delayed queue service
     * @param dueTimerStreamPublisher due timer stream publisher
     * @param timerRepository timer repository
     */
    public DueTimerForwarder(
        DelayedQueueService delayedQueueService,
        DueTimerStreamPublisher dueTimerStreamPublisher,
        TimerRepository timerRepository
    ) {
        this.delayedQueueService = delayedQueueService;
        this.dueTimerStreamPublisher = dueTimerStreamPublisher;
        this.timerRepository = timerRepository;
    }

    /**
     * Starts the blocking queue forwarding loop after the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        pollLoop()
            .onErrorContinue((error, value) -> log.error("Due timer forwarder loop failed", error))
            .subscribe();
    }

    private Flux<?> pollLoop() {
        return Flux.defer(() -> delayedQueueService.takeReadyTimer().flatMap(this::forwardDueTimer))
            .repeat();
    }

    private Mono<Void> forwardDueTimer(UUID timerId) {
        long now = System.currentTimeMillis();
        return timerRepository.markReady(timerId, now)
            .flatMap(rowsUpdated -> {
                if (rowsUpdated == 0) {
                    log.warn("Skipping due timer {} because status could not be changed to READY", timerId);
                    return Mono.empty();
                }
                return dueTimerStreamPublisher.publish(new DueTimerMessage(timerId))
                    .doOnSuccess(ignored -> log.debug("Forwarded due timer {}", timerId));
            })
            .then();
    }
}
