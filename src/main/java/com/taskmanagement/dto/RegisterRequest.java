package com.taskmanagement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.taskmanagement.validation.Utf8Length;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) @Utf8Length(max = 72) String password) {
    @Override
    public String toString() {
        return "RegisterRequest[credentials redacted]";
    }
}
