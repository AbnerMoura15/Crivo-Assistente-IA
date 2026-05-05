package com.ragbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Habilita @Scheduled do RateLimiterService
public class RagBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagBotApplication.class, args);
    }
}
