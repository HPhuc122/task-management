package com.taskmanagement.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("app.storage")
@Validated
public record ObjectStorageProperties(
        String endpoint,
        @NotBlank String region,
        String accessKey,
        String secretKey,
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String bucket,
        boolean pathStyleAccess,
        boolean createBucket,
        DataSize maxFileSize) {

    public ObjectStorageProperties {
        region = region == null ? "us-east-1" : region;
        bucket = bucket == null ? "task-attachments" : bucket;
        maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(10) : maxFileSize;
        if (maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("Attachment maximum file size must be positive");
        }
    }
}
