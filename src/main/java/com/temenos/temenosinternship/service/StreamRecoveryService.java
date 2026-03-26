package com.temenos.temenosinternship.service;

import com.temenos.temenosinternship.config.TimerProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.ByteBufferRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Recovers stale pending due-timer stream records and returns them to normal processing.
 */
@Service
public class StreamRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StreamRecoveryService.class);
    private static final Duration RECOVERY_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final int RECOVERY_BATCH_SIZE = 10;

    private final ReactiveRedisConnectionFactory connectionFactory;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final TimerProperties timerProperties;
    private final ProcessTimerConsumer processTimerConsumer;
    private final String recoveryConsumerName = "process-recovery-" + UUID.randomUUID();

    /**
     * Creates a stream recovery service.
     *
     * @param connectionFactory reactive Redis connection factory
     * @param redisTemplate reactive Redis template
     * @param timerProperties timer configuration properties
     * @param processTimerConsumer timer processing consumer
     */
    public StreamRecoveryService(
        ReactiveRedisConnectionFactory connectionFactory,
        ReactiveStringRedisTemplate redisTemplate,
        TimerProperties timerProperties,
        ProcessTimerConsumer processTimerConsumer
    ) {
        this.connectionFactory = connectionFactory;
        this.redisTemplate = redisTemplate;
        this.timerProperties = timerProperties;
        this.processTimerConsumer = processTimerConsumer;
    }

    /**
     * Starts periodic recovery of stale pending due-timer records.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Flux.interval(RECOVERY_POLL_INTERVAL)
            .concatMap(tick -> recoverPendingDueTimers())
            .onErrorContinue((error, value) -> log.error("Due stream recovery loop failed", error))
            .subscribe();
    }

    private Mono<Void> recoverPendingDueTimers() {
        return Mono.usingWhen(
            Mono.fromSupplier(connectionFactory::getReactiveConnection),
            this::recoverPendingDueTimers,
            ReactiveRedisConnection::closeLater
        );
    }

    private Mono<Void> recoverPendingDueTimers(ReactiveRedisConnection connection) {
        ByteBuffer streamKey = StandardCharsets.UTF_8.encode(timerProperties.getDueTimerStreamKey());
        Duration idleTimeout = Duration.ofMillis(timerProperties.getProcessClaimTimeoutMs());
        return connection.streamCommands()
            .xPending(streamKey, timerProperties.getProcessConsumerGroup(), Range.unbounded(), (long) RECOVERY_BATCH_SIZE, idleTimeout)
            .flatMapMany(messages -> Flux.fromIterable(messages))
            .concatMap(message -> reclaimAndProcess(connection, streamKey, idleTimeout, message))
            .then();
    }

    private Mono<Void> reclaimAndProcess(
        ReactiveRedisConnection connection,
        ByteBuffer streamKey,
        Duration idleTimeout,
        PendingMessage message
    ) {
        RedisStreamCommands.XClaimOptions claimOptions = RedisStreamCommands.XClaimOptions
            .minIdle(idleTimeout)
            .ids(message.getId());

        return connection.streamCommands()
            .xClaim(streamKey, timerProperties.getProcessConsumerGroup(), recoveryConsumerName, claimOptions)
            .next()
            .flatMap(record -> processClaimedRecord(record)
                .then(acknowledge(record.getId()))
                .doOnSuccess(ignored -> log.debug("Recovered pending due timer record {}", record.getId())))
            .switchIfEmpty(Mono.empty());
    }

    private Mono<Void> processClaimedRecord(ByteBufferRecord record) {
        String timerIdValue = readString(record.getValue().get(StandardCharsets.UTF_8.encode("timerId")));
        if (timerIdValue == null) {
            return Mono.empty();
        }
        return processTimerConsumer.recoverDueTimer(UUID.fromString(timerIdValue));
    }

    private Mono<Void> acknowledge(RecordId recordId) {
        return redisTemplate.opsForStream()
            .acknowledge(timerProperties.getDueTimerStreamKey(), timerProperties.getProcessConsumerGroup(), recordId)
            .then();
    }

    private String readString(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        return StandardCharsets.UTF_8.decode(buffer.duplicate()).toString();
    }
}
