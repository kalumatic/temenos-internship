package com.temenos.temenosinternship.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class TestController {
    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    @GetMapping("/test")
    public Mono<ResponseEntity<String>> test() {
        log.debug("/test called");
        return Mono.just(ResponseEntity.ok("test ok"));
    }
}
