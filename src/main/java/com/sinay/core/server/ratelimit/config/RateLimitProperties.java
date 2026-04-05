package com.sinay.core.server.ratelimit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private Defaults defaults = new Defaults();
    private Cache cache = new Cache();

    @Data
    public static class Defaults {
        private int capacity = 50;
        private int refillTokens = 50;
        private int refillDurationMinutes = 1;
        private int banThreshold = 10;
        private int banDurationMinutes = 10;
        private int warningThresholdPercent = 80;
    }

    @Data
    public static class Cache {
        private int strikeTtlMinutes = 1;
        private long maxSize = 10000;
    }
}
