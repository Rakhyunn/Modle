package com.modle.global.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 소셜 로그인(OAuth) 성공 후, 백엔드가 직접 토큰 쿠키를 심는 대신 프론트엔드로 전달할
 * 1회용 코드를 발급/소비한다. 프론트엔드는 이 코드를 same-origin 프록시(/api/v1/auth/oauth/exchange)로
 * 교환하고, 그 응답에서 인증 쿠키가 프론트엔드 도메인의 first-party 쿠키로 설정된다.
 * (프론트/백엔드가 서로 다른 도메인에 배포되어 백엔드 도메인 쿠키를 프론트가 못 읽는 문제 해결)
 */
@Service
@RequiredArgsConstructor
public class OAuthCodeService {
    private final RedisTemplate<String, String> redisTemplate;

    private static final String CODE_PREFIX = "oauth:code:";
    // 발급 직후 즉시 교환되므로 짧게(60초) 유지한다.
    private static final long CODE_EXPIRE_SECONDS = 60;

    /** 코드 발급 + Redis 저장({code -> userId}). 발급된 코드 문자열 반환. */
    public String issue(Long userId) {
        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                CODE_PREFIX + code,
                String.valueOf(userId),
                CODE_EXPIRE_SECONDS,
                TimeUnit.SECONDS
        );
        return code;
    }

    /** 코드 검증 + 원자적 소비(즉시 삭제 → 1회용 보장). 유효하지 않으면 null 반환. */
    public Long consume(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String userId = redisTemplate.opsForValue().getAndDelete(CODE_PREFIX + code);
        if (userId == null) {
            return null;
        }
        return Long.valueOf(userId);
    }
}
