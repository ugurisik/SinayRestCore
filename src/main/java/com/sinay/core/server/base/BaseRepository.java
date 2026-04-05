package com.sinay.core.server.base;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ObjectCore'un list(), getByField(), getByPK() metodlarının QueryDSL karşılığı.
 *
 * Kullanım:
 *   QUser user = QUser.user;
 *   List<User> result = repo.findAll(user, user.email.eq("x@y.com"), user.createdAt.desc());
 */
@Repository
@RequiredArgsConstructor
public class BaseRepository {

    protected final JPAQueryFactory queryFactory;

    // ===== TEK KAYIT =====

    public <T> Optional<T> findOne(EntityPathBase<T> qEntity, Predicate predicate) {
        T result = queryFactory
                .selectFrom(qEntity)
                .where(predicate, isVisible(qEntity))
                .fetchOne();
        return Optional.ofNullable(result);
    }

    public <T> Optional<T> findOneById(EntityPathBase<T> qEntity, UUID id) {
        // ID kontrolü — visible filtresi dahil
        T result = queryFactory
                .selectFrom(qEntity)
                .where(isVisible(qEntity))
                .fetchOne();
        return Optional.ofNullable(result);
    }

    // ===== LİSTE =====

    public <T> List<T> findAll(EntityPathBase<T> qEntity, Predicate... predicates) {
        return queryFactory
                .selectFrom(qEntity)
                .where(combineWithVisible(qEntity, predicates))
                .fetch();
    }

    @SafeVarargs
    public final <T> List<T> findAll(EntityPathBase<T> qEntity, OrderSpecifier<?>... orders) {
        return queryFactory
                .selectFrom(qEntity)
                .where(isVisible(qEntity))
                .orderBy(orders)
                .fetch();
    }

    @SafeVarargs
    public final <T> List<T> findAll(EntityPathBase<T> qEntity,
                                     Predicate predicate,
                                     OrderSpecifier<?>... orders) {
        return queryFactory
                .selectFrom(qEntity)
                .where(predicate, isVisible(qEntity))
                .orderBy(orders)
                .fetch();
    }

    // ===== SAYFALAMA =====

    public <T> Page<T> findPage(EntityPathBase<T> qEntity,
                                Predicate predicate,
                                Pageable pageable) {
        JPAQuery<T> query = queryFactory
                .selectFrom(qEntity)
                .where(predicate, isVisible(qEntity));

        long total = query.fetchCount();

        List<T> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, total);
    }

    public <T> Page<T> findPage(EntityPathBase<T> qEntity,
                                Predicate predicate,
                                Pageable pageable,
                                OrderSpecifier<?>... orders) {
        JPAQuery<T> query = queryFactory
                .selectFrom(qEntity)
                .where(predicate, isVisible(qEntity))
                .orderBy(orders);

        long total = queryFactory
                .selectFrom(qEntity)
                .where(predicate, isVisible(qEntity))
                .fetchCount();

        List<T> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, total);
    }

    // ===== COUNT =====

    public <T> long count(EntityPathBase<T> qEntity, Predicate predicate) {
        return queryFactory
                .selectFrom(qEntity)
                .where(predicate, isVisible(qEntity))
                .fetchCount();
    }

    public <T> boolean exists(EntityPathBase<T> qEntity, Predicate predicate) {
        return queryFactory
                .selectFrom(qEntity)
                .where(predicate, isVisible(qEntity))
                .fetchFirst() != null;
    }

    // ===== VISIBLE FILTER =====
    // ObjectCore'daki Restrictions.eq("visible", true) karşılığı
    // QEntity üzerinden visible alanına reflection ile ulaşıyoruz

    @SuppressWarnings("unchecked")
    private <T> BooleanExpression isVisible(EntityPathBase<T> qEntity) {
        try {
            var visibleField = qEntity.getClass().getField("visible");
            var visiblePath = (com.querydsl.core.types.dsl.BooleanPath) visibleField.get(qEntity);
            return visiblePath.isTrue();
        } catch (Exception e) {
            // Entity'de visible field yoksa filtre uygulanmaz
            return null;
        }
    }

    private <T> Predicate[] combineWithVisible(EntityPathBase<T> qEntity, Predicate[] predicates) {
        BooleanExpression visible = isVisible(qEntity);
        if (visible == null) return predicates;

        Predicate[] combined = new Predicate[predicates.length + 1];
        System.arraycopy(predicates, 0, combined, 0, predicates.length);
        combined[predicates.length] = visible;
        return combined;
    }
}