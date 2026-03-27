package com.temenos.temenosinternship.controller;

import com.temenos.temenosinternship.api.TimersApi;
import com.temenos.temenosinternship.domain.CreateTimerCommand;
import com.temenos.temenosinternship.mapper.TimerMapper;
import com.temenos.temenosinternship.model.CreateTimerRequest;
import com.temenos.temenosinternship.model.Timer;
import com.temenos.temenosinternship.model.TimerStatus;
import com.temenos.temenosinternship.model.TimersResponse;
import com.temenos.temenosinternship.repository.TimerRepository;
import com.temenos.temenosinternship.service.RequestStreamPublisher;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
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
    @PostMapping(path = "/api/timers", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Timer>> createTimer(
        @RequestBody
        Mono<CreateTimerRequest> createTimerRequest,
        ServerWebExchange exchange
    ) {
        return createTimerRequest.flatMap(request -> {
            UUID timerId = UUID.randomUUID();
            CreateTimerCommand command = new CreateTimerCommand(
                timerId,
                request.getCreated(),
                request.getDelay(),
                request.getCallbackUrl(),
                request.getCsrfToken()
            );
            Timer response = new Timer()
                .timerId(timerId.toString())
                .created(request.getCreated())
                .delay(request.getDelay())
                .callbackUrl(request.getCallbackUrl())
                .csrfToken(request.getCsrfToken())
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
    @GetMapping(path = "/api/timers", produces = "application/json")
    public Mono<ResponseEntity<TimersResponse>> getAllTimers(ServerWebExchange exchange) {
        return timerRepository.findAllTimers()
            .map(timerMapper::toModel)
            .collectList()
            .map(timers -> ResponseEntity.ok(new TimersResponse().timers(timers)));
    }

    /**
     * Returns a single timer by identifier.
     *
     * @param timerId timer identifier
     * @param exchange current web exchange
     * @return timer response or not found
     */
    @Override
    @GetMapping(path = "/api/timers/{timer_id}", produces = "application/json")
    public Mono<ResponseEntity<Timer>> getTimer(@PathVariable("timer_id") String timerId, ServerWebExchange exchange) {
        return timerRepository.findTimerById(UUID.fromString(timerId))
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
    @DeleteMapping(path = "/api/timers/{timer_id}", produces = "application/json")
    public Mono<ResponseEntity<Void>> deleteTimer(@PathVariable("timer_id") String timerId, ServerWebExchange exchange) {
        UUID parsedTimerId = UUID.fromString(timerId);
        return timerRepository.existsById(parsedTimerId)
            .flatMap(exists -> exists
                ? timerRepository.deleteById(parsedTimerId).thenReturn(ResponseEntity.ok().<Void>build())
                : Mono.just(ResponseEntity.notFound().build()));
    }
}
