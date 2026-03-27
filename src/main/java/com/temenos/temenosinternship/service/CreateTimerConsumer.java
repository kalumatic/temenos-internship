package com.temenos.temenosinternship.service;

import com.temenos.temenosinternship.config.TimerProperties;
import com.temenos.temenosinternship.domain.CreateTimerCommand;
import com.temenos.temenosinternship.domain.TimerStatus;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Consumes timer creation requests from the Redis request stream.
 */
@Service
public class CreateTimerConsumer {

    private static final Logger log = LoggerFactory.getLogger(CreateTimerConsumer.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TimerRepository timerRepository;
    private final TimerProperties timerProperties;
    private final TimerService timerService;
    private final DelayedQueueService delayedQueueService;

    /**
     * Creates a timer creation consumer.
     *
     * @param redisTemplate reactive Redis template
     * @param timerRepository reactive timer repository
     * @param timerProperties timer configuration properties
     * @param timerService timer scheduling rules
     * @param delayedQueueService delayed queue service
     */
    public CreateTimerConsumer(
        ReactiveStringRedisTemplate redisTemplate,
        TimerRepository timerRepository,
        TimerProperties timerProperties,
        TimerService timerService,
        DelayedQueueService delayedQueueService
    ) {
        this.redisTemplate = redisTemplate;
        this.timerRepository = timerRepository;
        this.timerProperties = timerProperties;
        this.timerService = timerService;
        this.delayedQueueService = delayedQueueService;
    }

    /**
     * Starts the request stream polling loop after the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        pollLoop()
            .onErrorContinue((error, value) -> log.error("Create consumer loop failed", error))
            .subscribe();
    }

    private Flux<?> pollLoop() {
        Consumer consumer = Consumer.from(timerProperties.getCreateConsumerGroup(), "create-consumer-" + UUID.randomUUID());
        StreamReadOptions options = StreamReadOptions.empty()
            .count(10)
            .block(Duration.ofSeconds(2));
        StreamOffset<String> offset = StreamOffset.create(timerProperties.getRequestStreamKey(), ReadOffset.lastConsumed());

        return Flux.defer(() -> redisTemplate.opsForStream()
                .read(consumer, options, offset)
                .map(record -> (MapRecord<String, String, String>) (MapRecord<?, ?, ?>) record)
                .concatMap(this::handleRecord))
            .repeat();
    }

    private Mono<Void> handleRecord(MapRecord<String, String, String> record) {
        if (!isCreateCommand(record)) {
            return redisTemplate.opsForStream()
                .acknowledge(timerProperties.getRequestStreamKey(), timerProperties.getCreateConsumerGroup(), record.getId())
                .then()
                .doOnSuccess(ignored -> log.debug("Skipped non-create record {}", record.getId()));
        }

        CreateTimerCommand command = mapCommand(record);
        long now = System.currentTimeMillis();
        boolean near = timerService.isNear(command.created(), command.delay(), now);
        TimerStatus initialStatus = near ? TimerStatus.SCHEDULED : TimerStatus.PENDING;

        Mono<Integer> insertOperation = timerRepository.insertTimer(
            command.timerId(),
            command.created(),
            command.delay(),
            command.callbackUrl(),
            command.csrfToken(),
            initialStatus,
            0,
            now
        );

        Mono<Void> schedulingOperation = near
            ? delayedQueueService.schedule(command.timerId(), command.created(), command.delay())
            : Mono.empty();

        return insertOperation
            .then(schedulingOperation)
            .then(redisTemplate.opsForStream()
                .acknowledge(timerProperties.getRequestStreamKey(), timerProperties.getCreateConsumerGroup(), record.getId())
                .then())
            .doOnSuccess(ignored -> log.debug("Processed timer creation request {}", command.timerId()));
    }

    private boolean isCreateCommand(MapRecord<String, String, String> record) {
        return record.getValue().get("timerId") != null
            && record.getValue().get("created") != null
            && record.getValue().get("delay") != null
            && record.getValue().get("callbackUrl") != null
            && record.getValue().get("csrfToken") != null;
    }

    private CreateTimerCommand mapCommand(MapRecord<String, String, String> record) {
        return new CreateTimerCommand(
            UUID.fromString(record.getValue().get("timerId")),
            Long.parseLong(record.getValue().get("created")),
            Integer.parseInt(record.getValue().get("delay")),
            record.getValue().get("callbackUrl"),
            record.getValue().get("csrfToken")
        );
    }
}
