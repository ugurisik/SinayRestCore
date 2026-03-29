package com.sinay.core.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sinay.core.core.ObjectCoreInjector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryDslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * QueryDSL'in ana factory'si.
     * Tüm repository'lere inject edilir.
     *
     * Kullanım:
     *   QUser user = QUser.user;
     *   queryFactory.selectFrom(user).where(user.email.eq("...")).fetch();
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }

    /**
     * ObjectCore static utility class'ını başlatır.
     * EntityManager ve JPAQueryFactory'yi inject eder.
     */
    @Bean
    public ObjectCoreInjector objectCoreInjector() {
        return new ObjectCoreInjector();
    }
}