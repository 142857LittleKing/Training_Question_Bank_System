package com.questionbank.controller;

import com.questionbank.common.ApiResponse;
import com.questionbank.dto.AuthDtos.ChangePasswordRequest;
import com.questionbank.dto.AuthDtos.LoginRequest;
import com.questionbank.dto.AuthDtos.LoginResponse;
import com.questionbank.dto.AuthDtos.UserView;
import com.questionbank.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        return ApiResponse.ok(authService.me());
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(req.oldPassword(), req.newPassword());
        return ApiResponse.ok();
    }

    @GetMapping("/users")
    public ApiResponse<List<UserView>> users() {
        return ApiResponse.ok(authService.listUsers());
    }
}
