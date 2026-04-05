package com.sinay.core.server.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Ad boş olamaz")
    @Size(min = 2, max = 50)
    private String name;

    @NotBlank(message = "Soyad boş olamaz")
    @Size(min = 2, max = 50)
    private String surname;

    @NotBlank(message = "Kullanıcı adı boş olamaz")
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Kullanıcı adı sadece harf, rakam, nokta, tire ve alt çizgi içerebilir")
    private String username;

    @NotBlank(message = "Email boş olamaz")
    @Email(message = "Geçerli bir email giriniz")
    private String email;

    @NotBlank(message = "Şifre boş olamaz")
    @Size(min = 8, message = "Şifre en az 8 karakter olmalıdır")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "Şifre en az bir büyük harf, bir küçük harf ve bir rakam içermelidir"
    )
    private String password;

    @Size(max = 20)
    private String phone;
}