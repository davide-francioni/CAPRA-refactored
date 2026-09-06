package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared application beans.
 * <p>
 * This class used to define two fixed chat clients, one per provider, selected by name
 * at the point of use. That arrangement bound each role to a model at compile time and
 * has been replaced by {@link ChatClientRegistry}, which builds a client per
 * configuration declared in the blueprint and resolves it by role.
 * <p>
 * What remains here has nothing to do with language models and is unaffected by the
 * change.
 */
@Configuration
public class AiConfig {

    /**
     * Executor with virtual threads for parallel agent execution.
     * <p>
     * Note that with several roles served by the same endpoint, the four analysis
     * agents produce genuine concurrency on that endpoint — which is why the serving
     * engine must be configured for reproducible output under concurrent load.
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
