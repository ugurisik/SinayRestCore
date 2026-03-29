package com.sinay.core.ratelimit.exception;

public class RateLimitException extends RuntimeException {
    private final int retryAfterSeconds;
    private final long remainingTokens;
    private final int remainingStrikes;

    public RateLimitException(String message) {
        super(message);
        this.retryAfterSeconds = 0;
        this.remainingTokens = 0;
        this.remainingStrikes = 0;
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
        this.retryAfterSeconds = 0;
        this.remainingTokens = 0;
        this.remainingStrikes = 0;
    }

    public RateLimitException(String message, int retryAfterSeconds, long remainingTokens, int remainingStrikes) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.remainingTokens = remainingTokens;
        this.remainingStrikes = remainingStrikes;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }

    public int getRemainingStrikes() {
        return remainingStrikes;
    }
}
