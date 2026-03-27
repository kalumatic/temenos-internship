package com.temenos.temenosinternship.controller;

import com.temenos.temenosinternship.model.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Test endpoint for observing timer callback delivery.
 */
@RestController
public class CallbackController {

    private static final Logger log = LoggerFactory.getLogger(CallbackController.class);

    /**
     * Receives a timer callback and logs the payload.
     *
     * @param timer callback payload
     * @return success response
     */
    @PostMapping(path = "/callback", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Void>> receiveCallback(@RequestBody Mono<Timer> timer) {
        return timer
            .doOnNext(payload -> log.info(
                "Received callback for timer {} with status {} and CSRF token {}",
                payload.getTimerId(),
                payload.getStatus(),
                payload.getCsrfToken()
            ))
            .thenReturn(ResponseEntity.ok().<Void>build());
    }
}
