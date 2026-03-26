package com.temenos.temenosinternship.service;

import com.temenos.temenosinternship.config.TimerProperties;
import com.temenos.temenosinternship.domain.DueTimerMessage;
import java.util.Map;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Publishes due timers to the Redis due timer stream.
 */
@Service
public class DueTimerStreamPublisher {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TimerProperties timerProperties;

    /**
     * Creates a due timer stream publisher.
     *
     * @param redisTemplate reactive Redis template
     * @param timerProperties timer configuration properties
     */
    public DueTimerStreamPublisher(ReactiveStringRedisTemplate redisTemplate, TimerProperties timerProperties) {
        this.redisTemplate = redisTemplate;
        this.timerProperties = timerProperties;
    }

    /**
     * Publishes a due timer message to the due timer stream.
     *
     * @param message due timer message
     * @return completion signal
     */
    public Mono<Void> publish(DueTimerMessage message) {
        MapRecord<String, String, String> record = MapRecord.create(
            timerProperties.getDueTimerStreamKey(),
            Map.of("timerId", message.timerId().toString())
        );
        return redisTemplate.opsForStream()
            .add(record)
            .then();
    }
}
