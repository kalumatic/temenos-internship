package com.temenos.temenosinternship.service;

import com.temenos.temenosinternship.config.TimerProperties;
import com.temenos.temenosinternship.domain.CreateTimerCommand;
import java.util.Map;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Publishes timer creation commands to the Redis request stream.
 */
@Service
public class RequestStreamPublisher {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TimerProperties timerProperties;

    /**
     * Creates a request stream publisher.
     *
     * @param redisTemplate reactive string Redis template
     * @param timerProperties timer configuration properties
     */
    public RequestStreamPublisher(ReactiveStringRedisTemplate redisTemplate, TimerProperties timerProperties) {
        this.redisTemplate = redisTemplate;
        this.timerProperties = timerProperties;
    }

    /**
     * Publishes a timer creation command to the request stream.
     *
     * @param command timer creation command
     * @return completion signal
     */
    public Mono<Void> publish(CreateTimerCommand command) {
        MapRecord<String, String, String> record = MapRecord.create(
            timerProperties.getRequestStreamKey(),
            Map.of(
                "timerId", command.timerId().toString(),
                "created", Long.toString(command.created()),
                "delay", Integer.toString(command.delay()),
                "callbackUrl", command.callbackUrl(),
                "csrfToken", command.csrfToken()
            )
        );
        return redisTemplate.opsForStream()
            .add(record)
            .then();
    }
}
