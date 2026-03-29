package com.sinay.core.ratelimit.exception;

import java.time.LocalDateTime;

public class RateLimitBanException extends RateLimitException {
    private final LocalDateTime banExpiry;
    private final int banDurationMinutes;

    public RateLimitBanException(String message) {
        super(message);
        this.banExpiry = null;
        this.banDurationMinutes = 0;
    }

    public RateLimitBanException(String message, Throwable cause) {
        super(message, cause);
        this.banExpiry = null;
        this.banDurationMinutes = 0;
    }

    public RateLimitBanException(String message, int banDurationMinutes, LocalDateTime banExpiry) {
        super(message, banDurationMinutes * 60, 0, 0); // retry-After saniye cinsinden
        this.banExpiry = banExpiry;
        this.banDurationMinutes = banDurationMinutes;
    }

    public LocalDateTime getBanExpiry() {
        return banExpiry;
    }

    public int getBanDurationMinutes() {
        return banDurationMinutes;
    }
}
