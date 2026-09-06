package com.example.demo.config;

/**
 * How a {@link RoleConfig} is turned into a chat client.
 * <p>
 * The third value is the one that makes the benchmark possible: engines such as vLLM
 * expose an OpenAI-compatible API, so the same client works against a local endpoint
 * by changing only the base URL. No new integration is required.
 */
public enum Provider {

    /** The real OpenAI API. */
    OPENAI,

    /** The real Anthropic API. */
    ANTHROPIC,

    /** A local server speaking the OpenAI protocol. */
    OPENAI_COMPATIBLE
}
