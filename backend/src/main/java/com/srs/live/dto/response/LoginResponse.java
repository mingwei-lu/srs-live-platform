package com.srs.live.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String uid;
    private String username;
    private String role;
    private String token;
}