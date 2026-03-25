package com.temenos.temenosinternship;

import com.temenos.temenosinternship.config.TimerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TimerProperties.class)
public class TemenosInternshipApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemenosInternshipApplication.class, args);
    }

}
