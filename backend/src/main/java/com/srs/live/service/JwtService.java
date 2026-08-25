package com.srs.live.service;

import com.srs.live.common.util.JwtUtil;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtUtil jwtUtil;

    public JwtService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String generateToken(String uid, String username, String role) {
        return jwtUtil.generateToken(uid, username, role);
    }
}