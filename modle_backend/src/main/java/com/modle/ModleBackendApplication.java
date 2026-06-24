package com.modle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@EnableJpaAuditing // 생성일, 수정일 자동화
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ModleBackendApplication {

    public static void main(String[] args) throws IOException {
        setupGcsCredentials();
        SpringApplication.run(ModleBackendApplication.class, args);
    }

    // 배포 환경에서 GCS_CREDENTIALS_JSON 환경변수를 임시 파일로 풀어서
    // GCS_CREDENTIALS_PATH 시스템 프로퍼티로 등록 (yml이 이 값을 읽음)
    private static void setupGcsCredentials() throws IOException {
        String credentialsJson = System.getenv("GCS_CREDENTIALS_JSON");
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            // Base64 디코딩 추가
            byte[] decoded = java.util.Base64.getDecoder().decode(credentialsJson.trim());
            Path tempFile = Files.createTempFile("gcs-key", ".json");
            Files.write(tempFile, decoded);
            System.setProperty("GCS_CREDENTIALS_PATH", "file:" + tempFile.toAbsolutePath());
        }
    }
}
