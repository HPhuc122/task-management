package com.taskmanagement.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class ObjectStorageBucketInitializer implements ApplicationRunner {
    private final S3Client s3;
    private final ObjectStorageProperties properties;

    public ObjectStorageBucketInitializer(S3Client s3, ObjectStorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.createBucket()) {
            return;
        }
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw new IllegalStateException("Could not inspect the attachment bucket", exception);
            }
            createBucket();
        } catch (SdkException exception) {
            throw new IllegalStateException("Could not connect to attachment storage", exception);
        }
    }

    private void createBucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (SdkException exception) {
            throw new IllegalStateException("Could not create the attachment bucket", exception);
        }
    }
}
