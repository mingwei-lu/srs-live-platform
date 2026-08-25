package com.srs.live.controller;

import com.srs.live.dto.request.LoginRequest;
import com.srs.live.dto.request.RegisterRequest;
import com.srs.live.dto.response.ApiResponse;
import com.srs.live.dto.response.LoginResponse;
import com.srs.live.dto.response.PageResult;
import com.srs.live.entity.User;
import com.srs.live.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(userService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @GetMapping("/check-phone")
    public ApiResponse<Boolean> checkPhone(@RequestParam String phone) {
        return ApiResponse.success(userService.isPhoneRegistered(phone));
    }

    @GetMapping("/{uid}")
    public ApiResponse<User> getUser(@PathVariable String uid) {
        return ApiResponse.success(userService.getByUid(uid));
    }

    @GetMapping
    public ApiResponse<PageResult<User>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(userService.pageUsers(page, size, keyword));
    }

    @PostMapping
    public ApiResponse<User> createUser(@RequestBody User user) {
        return ApiResponse.success(userService.createUser(user));
    }

    @PutMapping("/{uid}")
    public ApiResponse<Void> updateUser(@PathVariable String uid, @RequestBody Map<String, Object> data) {
        userService.updateUser(uid, data);
        return ApiResponse.success();
    }

    @DeleteMapping("/{uid}")
    public ApiResponse<Void> deleteUser(@PathVariable String uid) {
        userService.deleteUser(uid);
        return ApiResponse.success();
    }
}
