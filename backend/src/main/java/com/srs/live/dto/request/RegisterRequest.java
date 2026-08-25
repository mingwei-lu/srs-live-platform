package com.srs.live.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "phone required")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "invalid phone number")
    private String phone;

    private String username;

    private String company;

    private String department;

    @Pattern(regexp = "^\\d{0,4}$", message = "phone tail must be 0-4 digits")
    private String phoneTail;

    @NotBlank(message = "password required")
    @Pattern(regexp = "^.{6,32}$", message = "password length must be 6-32")
    private String password;
}