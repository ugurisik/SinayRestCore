package com.sinay.core.ratelimit.annotation;

import com.sinay.core.ratelimit.model.KeyType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Bucket kapasitesi (maksimum token sayısı)
     */
    int capacity() default 50;

    /**
     * Her refill'de eklenecek token sayısı
     */
    int refillTokens() default 50;

    /**
     * Reflush süresi (dakika)
     */
    int refillDurationMinutes() default 1;

    /**
     * Rate limit key tipi
     */
    KeyType keyType() default KeyType.IP_AND_USER;

    /**
     * Ban threshold (kaç kez üst üste aşıldığında)
     */
    int banThreshold() default 10;

    /**
     * Ban süresi (dakika)
     */
    int banDurationMinutes() default 10;
}
