package com.modle.domain.user.service;

import com.modle.domain.user.dto.TokenPair;
import com.modle.domain.user.entity.User;
import com.modle.domain.user.repository.UserRepository;
import com.modle.global.auth.JwtTokenProvider;
import com.modle.global.exception.CustomException;
import com.modle.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthTokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_PREFIX = "refresh:";
    // 회전 직후(grace period) 동안만 유효한 직전 refreshToken 보관용 키
    private static final String REFRESH_GRACE_PREFIX = "refresh:grace:";
    private static final long REFRESH_EXPIRE_DAYS = 7;
    // 동시 reissue·이중 reissue로 회전된 직전 토큰을 탈취로 오인하지 않도록 허용하는 유예 시간
    private static final long GRACE_PERIOD_SECONDS = 60;

    // Access Token 생성
    public String genAccessToken(User user) {
        String role = user.getRole() != null ? user.getRole().name() : "INCOMPLETE";
        return jwtTokenProvider.createAccessToken(user.getId(), role);
    }


    // Refresh Token 생성 + Redis 저장
    public String genRefreshToken(User user) {
        String role = user.getRole() != null ? user.getRole().name() : "INCOMPLETE";
        String refreshToken = jwtTokenProvider.createRefreshToken(
                user.getId(), role
        );
        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + user.getId(),
                refreshToken,
                REFRESH_EXPIRE_DAYS,
                TimeUnit.DAYS
        );
        return refreshToken;
    }

    // Refresh Token 검증 + Token 발급
    public TokenPair reissueTokens(String refreshToken) {
        // 토큰 유효성 검증
        if (!jwtTokenProvider.isValid(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String key = REFRESH_PREFIX + userId;

        // Redis에 저장된 토큰과 비교
        String savedToken = redisTemplate.opsForValue().get(key);
        if (savedToken == null) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        String role = jwtTokenProvider.getRole(refreshToken);

        // 현재 저장 토큰과 불일치하는 경우
        if (!savedToken.equals(refreshToken)) {
            // 직전에 회전된 토큰인지(grace period) 확인 —
            // 멀티탭/SSR·클라이언트 이중 reissue로 거의 동시에 들어온 요청은 같은 직전 토큰을
            // 들고 오므로, 탈취로 오인하지 않고 이미 발급된 현재 refreshToken으로 수렴시킨다.
            String graceToken = redisTemplate.opsForValue().get(REFRESH_GRACE_PREFIX + userId);
            if (refreshToken.equals(graceToken)) {
                // 재회전하지 않고 새 accessToken만 발급 + 현재 유효한 refreshToken을 그대로 반환
                String accessToken = jwtTokenProvider.createAccessToken(userId, role);
                return new TokenPair(accessToken, savedToken);
            }

            // grace period도 지난 진짜 불일치 -> 탈취 의심 -> 강제 로그아웃
            deleteRefreshToken(userId);
            throw new CustomException(ErrorCode.TOKEN_STOLEN);
        }

        // 일치 → 새 토큰 발급 (Rotation)
        String newAccessToken = jwtTokenProvider.createAccessToken(userId, role);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId, role);

        // 직전 토큰을 grace period 동안 보관 — 회전 직후 같은 토큰으로 들어오는 동시 요청 허용
        redisTemplate.opsForValue().set(
                REFRESH_GRACE_PREFIX + userId,
                refreshToken,
                GRACE_PERIOD_SECONDS,
                TimeUnit.SECONDS
        );

        // Redis에 새 Refresh Token 저장
        redisTemplate.opsForValue().set(
                key,
                newRefreshToken,
                REFRESH_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        return new TokenPair(newAccessToken, newRefreshToken);
    }

    // Refresh Token 삭제 (로그아웃 / 탈취 의심) — grace 토큰도 함께 제거해 유예창을 닫는다
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
        redisTemplate.delete(REFRESH_GRACE_PREFIX + userId);
    }
}
