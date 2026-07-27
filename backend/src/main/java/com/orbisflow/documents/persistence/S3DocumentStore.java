package com.orbisflow.documents.persistence;

import java.io.InputStream;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3DocumentStore {
    private final S3Client s3;
    private final S3Properties properties;

    public S3DocumentStore(S3Client s3, S3Properties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    public void put(String key, byte[] content, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
    }

    public StoredObject open(String key) {
        ResponseInputStream<GetObjectResponse> input = s3.getObject(
                GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build());
        return new StoredObject(input, input.response().contentLength());
    }

    public void deleteQuietly(String key) {
        try {
            s3.deleteObject(request -> request.bucket(properties.bucket()).key(key));
        } catch (RuntimeException ignored) {
            // Best-effort compensation for a failed database transaction.
        }
    }

    public record StoredObject(
            InputStream inputStream, long contentLength
    ) implements AutoCloseable {
        @Override
        public void close() throws java.io.IOException {
            inputStream.close();
        }
    }
}
