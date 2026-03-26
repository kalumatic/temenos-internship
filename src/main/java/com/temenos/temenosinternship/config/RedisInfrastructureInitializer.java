package com.temenos.temenosinternship.config;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Creates required Redis stream infrastructure during application startup.
 */
@Component
public class RedisInfrastructureInitializer {

    private static final Logger log = LoggerFactory.getLogger(RedisInfrastructureInitializer.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TimerProperties timerProperties;

    /**
     * Creates a Redis infrastructure initializer.
     *
     * @param redisTemplate reactive Redis template
     * @param timerProperties timer configuration properties
     */
    public RedisInfrastructureInitializer(
        ReactiveStringRedisTemplate redisTemplate,
        TimerProperties timerProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.timerProperties = timerProperties;
    }

    /**
     * Ensures stream keys and consumer groups exist once the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        ensureGroup(timerProperties.getRequestStreamKey(), timerProperties.getCreateConsumerGroup())
            .then(ensureGroup(timerProperties.getDueTimerStreamKey(), timerProperties.getProcessConsumerGroup()))
            .doOnSuccess(ignored -> log.debug("Redis stream groups initialized"))
            .doOnError(error -> log.error("Failed to initialize Redis stream groups", error))
            .subscribe();
    }

    private Mono<Void> ensureGroup(String streamKey, String groupName) {
        MapRecord<String, String, String> seedRecord = MapRecord.create(streamKey, Map.of("bootstrap", "true"));
        return redisTemplate.opsForStream()
            .add(seedRecord)
            .then(redisTemplate.opsForStream()
                .createGroup(streamKey, ReadOffset.from("0-0"), groupName)
                .onErrorResume(error -> Mono.empty()))
            .then();
    }
}
