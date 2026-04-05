package com.sinay.core.server.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email veya username boş olamaz")
    private String identifier;  // email veya username

    @NotBlank(message = "Şifre boş olamaz")
    private String password;
}