package com.temenos.temenosinternship.service;

import com.temenos.temenosinternship.config.TimerProperties;
import com.temenos.temenosinternship.domain.TimerEntity;
import com.temenos.temenosinternship.domain.TimerStatus;
import com.temenos.temenosinternship.mapper.TimerMapper;
import com.temenos.temenosinternship.repository.TimerRepository;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Consumes due timers from the Redis stream and updates their processing state.
 */
@Service
public class ProcessTimerConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProcessTimerConsumer.class);
    private static final Duration CLAIM_RETRY_DELAY = Duration.ofMillis(200);
    private static final int CLAIM_RETRY_ATTEMPTS = 5;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TimerRepository timerRepository;
    private final TimerProperties timerProperties;
    private final TimerService timerService;
    private final DelayedQueueService delayedQueueService;
    private final TimerMapper timerMapper;
    private final WebClient webClient;

    /**
     * Creates a process timer consumer.
     *
     * @param redisTemplate reactive Redis template
     * @param timerRepository timer repository
     * @param timerProperties timer configuration properties
     * @param timerService timer scheduling rules
     * @param delayedQueueService delayed queue service
     * @param timerMapper timer mapper
     * @param webClientBuilder web client builder
     */
    public ProcessTimerConsumer(
        ReactiveStringRedisTemplate redisTemplate,
        TimerRepository timerRepository,
        TimerProperties timerProperties,
        TimerService timerService,
        DelayedQueueService delayedQueueService,
        TimerMapper timerMapper,
        WebClient.Builder webClientBuilder
    ) {
        this.redisTemplate = redisTemplate;
        this.timerRepository = timerRepository;
        this.timerProperties = timerProperties;
        this.timerService = timerService;
        this.delayedQueueService = delayedQueueService;
        this.timerMapper = timerMapper;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Starts the due timer processing loop after the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        pollLoop()
            .onErrorContinue((error, value) -> log.error("Process consumer loop failed", error))
            .subscribe();
    }

    private Flux<?> pollLoop() {
        Consumer consumer = Consumer.from(timerProperties.getProcessConsumerGroup(), "process-consumer-" + UUID.randomUUID());
        StreamReadOptions options = StreamReadOptions.empty()
            .count(10)
            .block(Duration.ofSeconds(2));
        StreamOffset<String> offset = StreamOffset.create(timerProperties.getDueTimerStreamKey(), ReadOffset.lastConsumed());

        return Flux.defer(() -> redisTemplate.opsForStream()
                .read(consumer, options, offset)
                .map(record -> (MapRecord<String, String, String>) (MapRecord<?, ?, ?>) record)
                .concatMap(this::handleRecord))
            .repeat();
    }

    private Mono<Void> handleRecord(MapRecord<String, String, String> record) {
        if (!isDueTimerRecord(record)) {
            return acknowledge(record)
                .doOnSuccess(ignored -> log.debug("Skipped non-due record {}", record.getId()));
        }

        UUID timerId = UUID.fromString(record.getValue().get("timerId"));

        return processDueTimer(timerId, false)
            .then(acknowledge(record))
            .doOnSuccess(ignored -> log.debug("Processed due timer {}", timerId));
    }

    /**
     * Reprocesses a claimed due timer after a stale pending stream entry is recovered.
     *
     * @param timerId timer identifier
     * @return completion signal
     */
    public Mono<Void> recoverDueTimer(UUID timerId) {
        return processDueTimer(timerId, true)
            .doOnSuccess(ignored -> log.debug("Recovered due timer {}", timerId));
    }

    private Mono<Boolean> claimForProcessing(UUID timerId, int retriesRemaining) {
        return timerRepository.markProcessing(timerId, System.currentTimeMillis())
            .flatMap(rowsUpdated -> {
                if (rowsUpdated > 0) {
                    return Mono.just(true);
                }
                if (retriesRemaining == 0) {
                    return Mono.just(false);
                }
                return Mono.delay(CLAIM_RETRY_DELAY)
                    .then(claimForProcessing(timerId, retriesRemaining - 1));
            });
    }

    private Mono<Void> processDueTimer(UUID timerId, boolean recoveryFlow) {
        return timerRepository.findTimerById(timerId)
            .flatMap(timer -> {
                if (recoveryFlow && timer.getStatus() == TimerStatus.PROCESSING) {
                    return executeTimer(timer);
                }
                if (timer.getStatus() != TimerStatus.READY) {
                    return Mono.empty();
                }
                return claimForProcessing(timerId, CLAIM_RETRY_ATTEMPTS)
                    .flatMap(claimed -> claimed
                        ? timerRepository.findTimerById(timerId).flatMap(this::executeTimer)
                        : Mono.empty());
            })
            .then();
    }

    private Mono<Void> executeTimer(TimerEntity timer) {
        return processTimer(timer)
            .then(timerRepository.markCompleted(timer.getTimerId(), System.currentTimeMillis()).then())
            .onErrorResume(error -> handleFailure(timer));
    }

    private Mono<Void> handleFailure(TimerEntity timer) {
        int nextAttempt = timer.getAttempts() + 1;
        long now = System.currentTimeMillis();

        if (timerService.shouldRetry(nextAttempt)) {
            long retryDelayMillis = timerService.calculateRetryDelayMillis(nextAttempt);
            return timerRepository.markRetryScheduled(timer.getTimerId(), now)
                .flatMap(rowsUpdated -> rowsUpdated > 0
                    ? delayedQueueService.scheduleRetry(timer.getTimerId(), retryDelayMillis)
                    : Mono.empty());
        }

        return timerRepository.markFailed(timer.getTimerId(), now).then();
    }

    private Mono<Void> processTimer(TimerEntity timer) {
        return webClient.post()
            .uri(timer.getCallbackUrl())
            .bodyValue(timerMapper.toModel(timer))
            .retrieve()
            .toBodilessEntity()
            .doOnSuccess(response -> log.info("Delivered callback for timer {} to {}", timer.getTimerId(), timer.getCallbackUrl()))
            .then();
    }

    private boolean isDueTimerRecord(MapRecord<String, String, String> record) {
        return record.getValue().get("timerId") != null;
    }

    private Mono<Void> acknowledge(MapRecord<String, String, String> record) {
        return redisTemplate.opsForStream()
            .acknowledge(timerProperties.getDueTimerStreamKey(), timerProperties.getProcessConsumerGroup(), record.getId())
            .then();
    }
}
