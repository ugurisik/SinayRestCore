package com.sinay.core.utils;

import com.sinay.core.dto.request.PageableRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Sayfalama utility sınıfı.
 * <p>
 * PageableRequest DTO'sunu Spring Pageable'a dönüştürür.
 * Validation ve default değerleri yönetir.
 */
public final class PageRequestUtils {

    private PageRequestUtils() {
        // Utility class - private constructor
    }

    /**
     * PageableRequest DTO'sundan Spring Pageable oluşturur.
     *
     * @param pageRequest Sayfalama parametreleri
     * @return Spring Pageable
     */
    public static Pageable toPageable(PageableRequest pageRequest) {
        if (pageRequest == null) {
            // Default değerlerle
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        Sort.Direction direction = parseDirection(pageRequest.getDirection());
        Sort sort = Sort.by(direction, pageRequest.getSort());

        return PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), sort);
    }

    /**
     * String direction değerini Sort.Direction'a dönüştürür.
     *
     * @param direction ASC veya DESC
     * @return Sort.Direction
     */
    private static Sort.Direction parseDirection(String direction) {
        if (direction == null) {
            return Sort.Direction.DESC;
        }

        try {
            return Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException e) {
            return Sort.Direction.DESC;
        }
    }

    /**
     * Page offset hesaplar (SQL LIMIT için).
     *
     * @param page Sayfa numarası
     * @param size Sayfa boyutu
     * @return Offset değeri
     */
    public static int calculateOffset(int page, int size) {
        return page * size;
    }

    /**
     * Sayfa numarasını normalize eder (negatifse 0 yapar).
     *
     * @param page Sayfa numarası
     * @return Normalize edilmiş sayfa
     */
    public static int normalizePage(int page) {
        return Math.max(0, page);
    }

    /**
     * Sayfa boyutunu normalize eder (min 1, max 100).
     *
     * @param size Sayfa boyutu
     * @return Normalize edilmiş boyut
     */
    public static int normalizeSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
