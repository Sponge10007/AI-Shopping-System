package com.aishop.modules.auth;

import com.aishop.modules.auth.dto.LoginRequest;
import com.aishop.modules.auth.dto.LoginResponse;
import com.aishop.modules.auth.dto.LogoutResponse;
import com.aishop.modules.auth.dto.RegisterRequest;
import com.aishop.modules.auth.dto.RegisterResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public RegisterResponse register(RegisterRequest request) {
        String role = request.role() == null || request.role().isBlank() ? "CUSTOMER" : request.role();
        String prefix = "MERCHANT".equals(role) ? "m" : "u";
        return new RegisterResponse(prefix + "10001", request.username(), role);
    }

    public LoginResponse login(LoginRequest request) {
        RegisterResponse user = new RegisterResponse("u10001", request.account(), "CUSTOMER");
        return new LoginResponse("dev-access-token", "dev-refresh-token", 7200, user);
    }

    public LogoutResponse logout() {
        return new LogoutResponse(true);
    }
}

