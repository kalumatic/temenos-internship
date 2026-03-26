package com.temenos.temenosinternship.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Schedules timers in Redis delayed and blocking queues.
 */
@Service
public class DelayedQueueService {

    private static final String READY_QUEUE_KEY = "timer:ready-queue";

    private final RedissonClient redissonClient;
    private final TimerService timerService;

    /**
     * Creates a delayed queue service.
     *
     * @param redissonClient Redisson client
     * @param timerService timer scheduling rules
     */
    public DelayedQueueService(RedissonClient redissonClient, TimerService timerService) {
        this.redissonClient = redissonClient;
        this.timerService = timerService;
    }

    /**
     * Schedules a timer for future execution using its current remaining timeout.
     *
     * @param timerId timer identifier
     * @param created timer creation timestamp
     * @param delaySeconds delay in seconds
     * @return completion signal
     */
    public Mono<Void> schedule(UUID timerId, long created, int delaySeconds) {
        return Mono.fromRunnable(() -> {
            long timeoutMillis = timerService.calculateTimeoutMillis(created, delaySeconds, System.currentTimeMillis());
            RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(READY_QUEUE_KEY);
            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
            delayedQueue.offer(timerId.toString(), timeoutMillis, TimeUnit.MILLISECONDS);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
