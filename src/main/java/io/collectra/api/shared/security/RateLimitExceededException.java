package io.collectra.api.shared.security;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() { super("Rate limit exceeded"); }
}
