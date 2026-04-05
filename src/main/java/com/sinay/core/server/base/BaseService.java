package com.sinay.core.server.base;

import com.sinay.core.server.exception.UsErrorCode;
import com.sinay.core.server.exception.UsException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tüm service'lerin extend edebileceği base service.
 * ObjectCore'daki save(), delete() (soft), load() mantığı burada.
 */
@RequiredArgsConstructor
public abstract class BaseService {

    protected final EntityManager entityManager;

    /**
     * ObjectCore.save() karşılığı.
     * ID null ise INSERT, doluysa UPDATE.
     */
    @Transactional
    protected <T extends BaseEntity> T persist(T entity) {
        if (entity.getId() == null) {
            entityManager.persist(entity);
            return entity;
        } else {
            return entityManager.merge(entity);
        }
    }

    /**
     * ObjectCore'daki shiftDelete=false mantığı — soft delete.
     * visible = false yapılır, DB'den silinmez.
     */
    @Transactional
    protected <T extends BaseEntity> void softDelete(T entity) {
        entity.setVisible(false);
        entityManager.merge(entity);
    }

    /**
     * Gerçek silme — dikkatli kullan.
     */
    @Transactional
    protected <T extends BaseEntity> void hardDelete(T entity) {
        entityManager.remove(entityManager.contains(entity) ? entity : entityManager.merge(entity));
    }

    /**
     * ID ile entity getir, yoksa 404.
     */
    protected <T extends BaseEntity> T findOrThrow(Class<T> cls, UUID id) {
        T entity = entityManager.find(cls, id);
        if (entity == null || Boolean.FALSE.equals(entity.getVisible())) {
            UsException.firlat(cls.getSimpleName() + " bulunamadı: " + id, UsErrorCode.RESOURCE_NOT_FOUND);
        }
        return entity;
    }
}