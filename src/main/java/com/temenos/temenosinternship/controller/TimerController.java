package com.temenos.temenosinternship.controller;

import com.temenos.temenosinternship.api.TimersApi;
import com.temenos.temenosinternship.domain.CreateTimerCommand;
import com.temenos.temenosinternship.mapper.TimerMapper;
import com.temenos.temenosinternship.model.CreateTimerRequest;
import com.temenos.temenosinternship.model.Timer;
import com.temenos.temenosinternship.model.TimerStatus;
import com.temenos.temenosinternship.repository.TimerRepository;
import com.temenos.temenosinternship.service.RequestStreamPublisher;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST entry point for timer operations.
 */
@RestController
public class TimerController implements TimersApi {

    private final RequestStreamPublisher requestStreamPublisher;
    private final TimerRepository timerRepository;
    private final TimerMapper timerMapper;

    /**
     * Creates a timer controller.
     *
     * @param requestStreamPublisher request stream publisher
     * @param timerRepository reactive timer repository
     * @param timerMapper entity to model mapper
     */
    public TimerController(
        RequestStreamPublisher requestStreamPublisher,
        TimerRepository timerRepository,
        TimerMapper timerMapper
    ) {
        this.requestStreamPublisher = requestStreamPublisher;
        this.timerRepository = timerRepository;
        this.timerMapper = timerMapper;
    }

    /**
     * Publishes a validated timer creation command and returns the generated timer identifier.
     *
     * @param createTimerRequest timer creation request
     * @param exchange current web exchange
     * @return API response with generated timer information
     */
    @Override
    public Mono<ResponseEntity<Timer>> createTimer(
        Mono<CreateTimerRequest> createTimerRequest,
        ServerWebExchange exchange
    ) {
        return createTimerRequest.flatMap(request -> {
            UUID timerId = UUID.randomUUID();
            CreateTimerCommand command = new CreateTimerCommand(timerId, request.getCreated(), request.getDelay());
            Timer response = new Timer()
                .timerId(timerId.toString())
                .created(request.getCreated())
                .delay(request.getDelay())
                .status(TimerStatus.PENDING)
                .attempts(0);
            return requestStreamPublisher.publish(command)
                .thenReturn(ResponseEntity.status(201).body(response));
        });
    }

    /**
     * Returns all persisted timers.
     *
     * @param exchange current web exchange
     * @return all timers
     */
    @Override
    public Mono<ResponseEntity<Flux<Timer>>> getAllTimers(ServerWebExchange exchange) {
        Flux<Timer> timers = timerRepository.findAll().map(timerMapper::toModel);
        return Mono.just(ResponseEntity.ok(timers));
    }

    /**
     * Returns a single timer by identifier.
     *
     * @param timerId timer identifier
     * @param exchange current web exchange
     * @return timer response or not found
     */
    @Override
    public Mono<ResponseEntity<Timer>> getTimer(String timerId, ServerWebExchange exchange) {
        return timerRepository.findById(UUID.fromString(timerId))
            .map(timerMapper::toModel)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a timer by identifier.
     *
     * @param timerId timer identifier
     * @param exchange current web exchange
     * @return deletion response
     */
    @Override
    public Mono<ResponseEntity<Void>> deleteTimer(String timerId, ServerWebExchange exchange) {
        UUID parsedTimerId = UUID.fromString(timerId);
        return timerRepository.existsById(parsedTimerId)
            .flatMap(exists -> exists
                ? timerRepository.deleteById(parsedTimerId).thenReturn(ResponseEntity.ok().<Void>build())
                : Mono.just(ResponseEntity.notFound().build()));
    }
}
