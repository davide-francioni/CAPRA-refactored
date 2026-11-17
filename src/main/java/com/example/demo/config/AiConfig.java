package com.example.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Infrastructure beans shared by the pipeline.
 * <p>
 * The two chat clients that used to live here — {@code analysisChatClient} and
 * {@code reportChatClient} — have moved to {@link ChatClientRegistry}. They fixed at
 * compile time which model served which role: two models, assigned by name, for seven
 * roles. The registry resolves them from the blueprint instead, one per role.
 * <p>
 * What remains here is unrelated to model selection.
 */
@Configuration
public class AiConfig {

    /**
     * Executor with virtual threads for parallel agent execution.
     */
    @Bean
    public ExecutorService agentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Shared ObjectMapper for JSON serialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
