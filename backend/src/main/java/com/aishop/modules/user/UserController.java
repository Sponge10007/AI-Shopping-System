package com.aishop.modules.user;

import com.aishop.common.response.ApiResponse;
import com.aishop.modules.user.dto.UpdateUserRequest;
import com.aishop.modules.user.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser() {
        return ApiResponse.ok(userService.getCurrentUser());
    }

    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateCurrentUser(@RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.updateCurrentUser(request));
    }
}

