package com.srs.live.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "phone or username required")
    private String phone;

    @NotBlank(message = "password required")
    private String password;
}