package com.taskmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @NotBlank(message = "name không được để trống") @Size(max = 100, message = "name tối đa 100 ký tự") String name,

        @Size(max = 2000, message = "description tối đa 2000 ký tự") String description) {
}