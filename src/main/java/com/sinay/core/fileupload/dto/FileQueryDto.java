package com.sinay.core.fileupload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sinay.core.fileupload.enums.FileCategory;
import lombok.Builder;
import lombok.Data;

/**
 * Dosya listelemek için sorgu parametreleri DTO'su.
 * Sayfalama, filtreleme, sıralama ve arama destekler.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileQueryDto {

    /**
     * Sayfa numarası (sıfırdan başlar).
     * Varsayılan: 0
     */
    @Builder.Default
    private int page = 0;

    /**
     * Sayfa boyutu (her sayfadaki öğe sayısı).
     * Varsayılan: 20, Maksimum: 100
     */
    @Builder.Default
    private int size = 20;

    /**
     * Dosya kategorisine göre filtreleme.
     * Opsiyonel - belirtilmezse tüm kategorilerdeki dosyalar döndürülür.
     */
    private FileCategory category;

    /**
     * Sıralama alanı.
     * Varsayılan: createdAt
     * Yaygın değerler: createdAt, originalFilename, fileSize, category
     */
    @Builder.Default
    private String sortBy = "createdAt";

    /**
     * Sıralama yönü.
     * Varsayılan: DESC
     * Değerler: ASC veya DESC
     */
    @Builder.Default
    private String sortDirection = "DESC";

    /**
     * Dosya adına göre filtrelemek için arama terimi.
     * originalFilename üzerinde büyük/küçük harf duyarsız kısmi eşleştirme yapar.
     * Opsiyonel - belirtilmezse filtreleme uygulanmaz.
     */
    private String search;

    /**
     * Sayfa boyutunu doğrular ve ayarlar.
     * Boyutun kabul edilebilir sınırlar içinde olmasını sağlar.
     *
     * @param size İstenen sayfa boyutu
     */
    public void setSize(int size) {
        if (size < 1) {
            this.size = 20;
        } else if (size > 100) {
            this.size = 100;
        } else {
            this.size = size;
        }
    }

    /**
     * Sayfa numarasını doğrular ve ayarlar.
     * Sayfanın negatif olmamasını sağlar.
     *
     * @param page İstenen sayfa numarası
     */
    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    /**
     * Sıralama yönünü doğrular ve normalleştirir.
     * Geçersiz bir değer sağlanırsa varsayılan olarak DESC kullanır.
     *
     * @param sortDirection Sıralama yönü (ASC veya DESC)
     */
    public void setSortDirection(String sortDirection) {
        if (sortDirection == null || (!sortDirection.equalsIgnoreCase("ASC") && !sortDirection.equalsIgnoreCase("DESC"))) {
            this.sortDirection = "DESC";
        } else {
            this.sortDirection = sortDirection.toUpperCase();
        }
    }
}
