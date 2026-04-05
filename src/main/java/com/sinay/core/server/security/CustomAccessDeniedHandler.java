package com.sinay.core.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinay.core.server.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 403 Forbidden yanıtı için custom handler.
 * <p>
 * Kullanıcı yetkisi olmayan bir işleme çalıştığında
 * ApiResponse formatında JSON yanıt döner.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<?> apiResponse = ApiResponse.fail("Bu işleme yetkiniz bulunmamaktadır");

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
