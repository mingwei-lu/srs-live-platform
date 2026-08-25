package com.srs.live.common.util;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MinioService {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioService(MinioClient minioClient,
                        @Value("${minio.bucket:live-recordings}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        initBucket();
    }

    private void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket created: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO init bucket failed: {}", e.getMessage());
        }
    }

    /** 上传文件到 MinIO */
    public String uploadFile(String objectName, File file, String contentType) {
        try (FileInputStream fis = new FileInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(fis, file.length(), -1)
                    .contentType(contentType)
                    .build());
            log.info("uploaded to MinIO: bucket={}, object={}", bucket, objectName);
            return getFileUrl(objectName);
        } catch (Exception e) {
            log.error("MinIO upload failed: object={}", objectName, e);
            return null;
        }
    }

    /** 获取文件访问 URL（7天有效） */
    public String getFileUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(7, TimeUnit.DAYS)
                    .build());
        } catch (Exception e) {
            log.error("MinIO get url failed: object={}", objectName, e);
            return null;
        }
    }

    /** 录播文件路径格式: recordings/{roomId}/{recordId}_{timestamp}.mp4 */
    public static String buildRecordingPath(String roomId, Long recordId) {
        return "recordings/" + roomId + "/" + recordId + "_" + System.currentTimeMillis() + ".mp4";
    }
}