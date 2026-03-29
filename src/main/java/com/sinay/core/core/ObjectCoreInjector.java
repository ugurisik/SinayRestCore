package com.sinay.core.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * ObjectCore için Spring entegrasyonu.
 * <p>
 * EntityManager, ObjectMapper, ApplicationContext ve JPAQueryFactory'yi static field'lara inject eder.
 * <p>
 * Kullanım:
 * <pre>
 * &#64;Configuration
 * public class AppConfig {
 *     &#64;Bean
 *     public ObjectCoreInjector objectCoreInjector() {
 *         return new ObjectCoreInjector();
 *     }
 * }
 * </pre>
 */
@Slf4j
@Component
public class ObjectCoreInjector implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext context) {
        EntityManager entityManager = context.getBean(EntityManager.class);
        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
        JPAQueryFactory queryFactory = new JPAQueryFactory(entityManager);

        ObjectCore.setEntityManager(entityManager);
        ObjectCore.setObjectMapper(objectMapper);
        ObjectCore.setApplicationContext(context);
        ObjectCore.setQueryFactory(queryFactory);

        log.info("ObjectCore initialized with EntityManager, ObjectMapper, ApplicationContext and JPAQueryFactory");
    }
}
