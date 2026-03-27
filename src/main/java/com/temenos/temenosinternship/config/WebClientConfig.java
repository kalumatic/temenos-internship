package com.temenos.temenosinternship.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Provides WebClient infrastructure for outbound callback requests.
 */
@Configuration
public class WebClientConfig {

    /**
     * Creates a reusable WebClient builder.
     *
     * @return WebClient builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
