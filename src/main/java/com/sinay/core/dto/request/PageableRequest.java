package com.sinay.core.dto.request;

import com.sinay.core.utils.PageRequestUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Sayfalama istek DTO'su.
 * <p>
 * Tüm list endpoint'lerinde kullanılır.
 * Varsayılan değerler application.yaml'dan alınır.
 *
 * @see PageRequestUtils
 */
@Data
@Schema(description = "Sayfalama parametreleri")
public class PageableRequest {

    @Schema(description = "Sayfa numarası (0'dan başlar)", example = "0")
    @Min(value = 0, message = "Sayfa numarası 0 veya daha büyük olmalı")
    private int page = 0;

    @Schema(description = "Sayfa başına kayıt sayısı", example = "20")
    @Min(value = 1, message = "Sayfa boyutu en az 1 olmalı")
    @Max(value = 100, message = "Sayfa boyutu en fazla 100 olabilir")
    private int size = 20;

    @Schema(description = "Sıralama alanı", example = "createdAt")
    private String sort = "createdAt";

    @Schema(description = "Sıralama yönü", example = "DESC", allowableValues = {"ASC", "DESC"})
    private String direction = "DESC";

    /**
     * Spring Pageable'a dönüştürür.
     */
    public org.springframework.data.domain.Pageable toPageable() {
        return PageRequestUtils.toPageable(this);
    }
}
