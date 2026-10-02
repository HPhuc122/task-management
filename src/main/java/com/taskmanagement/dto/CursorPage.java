package com.taskmanagement.dto;

import java.util.List;

public record CursorPage<T>(List<T> content, Long nextCursor, boolean hasNext) {
}