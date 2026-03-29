package com.sinay.core.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * ObjectCore için Spring entegrasyonu.
 * <p>
 * EntityManager, ObjectMapper, ApplicationContext ve JPAQueryFactory'yi static field'lara inject eder.
 * <p>
 * QueryDslConfig içinde @Bean olarak tanımlanır.
 */
@Slf4j
public class ObjectCoreInjector implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext context) {
        EntityManager entityManager = context.getBean(EntityManager.class);
        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
        JPAQueryFactory queryFactory = context.getBean(JPAQueryFactory.class);

        ObjectCore.setEntityManager(entityManager);
        ObjectCore.setObjectMapper(objectMapper);
        ObjectCore.setApplicationContext(context);
        ObjectCore.setQueryFactory(queryFactory);

        log.info("ObjectCore initialized with EntityManager, ObjectMapper, ApplicationContext and JPAQueryFactory");
    }
}
