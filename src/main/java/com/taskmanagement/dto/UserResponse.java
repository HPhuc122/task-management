package com.taskmanagement.dto;

import com.taskmanagement.entity.User;
import com.taskmanagement.entity.UserRole;

public record UserResponse(Long id, String email, UserRole role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
