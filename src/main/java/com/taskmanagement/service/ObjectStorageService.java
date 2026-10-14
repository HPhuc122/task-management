package com.taskmanagement.service;

import com.taskmanagement.config.ObjectStorageProperties;
import com.taskmanagement.exception.ObjectStorageException;
import java.io.InputStream;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ObjectStorageService {
    private final S3Client s3;
    private final String bucket;

    public ObjectStorageService(S3Client s3, ObjectStorageProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    public String upload(String objectKey, String contentType, long size, InputStream input) {
        try {
            return s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(objectKey).contentType(contentType).contentLength(size).build(),
                    RequestBody.fromInputStream(input, size)).eTag();
        } catch (SdkException exception) {
            throw new ObjectStorageException("Could not store the attachment", exception);
        }
    }

    public byte[] download(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return response.asByteArray();
        } catch (SdkException exception) {
            throw new ObjectStorageException("Could not read the attachment", exception);
        }
    }

    public void delete(String objectKey) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (SdkException exception) {
            throw new ObjectStorageException("Could not delete the attachment", exception);
        }
    }
}
