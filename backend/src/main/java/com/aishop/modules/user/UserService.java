package com.aishop.modules.user;

import com.aishop.modules.user.dto.UpdateUserRequest;
import com.aishop.modules.user.dto.UserResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class UserService {

    public UserResponse getCurrentUser() {
        return sampleUser("Alice");
    }

    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        String nickname = request.nickname() == null ? "Alice" : request.nickname();
        return new UserResponse(
                "u10001",
                "alice",
                request.phone() == null ? "13800000000" : request.phone(),
                "CUSTOMER",
                nickname,
                request.avatarUrl(),
                OffsetDateTime.now()
        );
    }

    private UserResponse sampleUser(String nickname) {
        return new UserResponse(
                "u10001",
                "alice",
                "13800000000",
                "CUSTOMER",
                nickname,
                "https://example.com/avatar.png",
                OffsetDateTime.now()
        );
    }
}

