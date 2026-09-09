package io.collectra.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class CollectraApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollectraApiApplication.class, args);
    }
}
