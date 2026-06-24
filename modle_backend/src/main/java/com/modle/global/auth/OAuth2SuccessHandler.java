package com.modle.global.auth;

import com.modle.domain.user.entity.User;
import com.modle.domain.user.entity.type.UserStatus;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final OAuthCodeService oAuthCodeService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2SecurityUser oAuth2SecurityUser = (OAuth2SecurityUser) authentication.getPrincipal();
        User user = oAuth2SecurityUser.getUser();

        // 로그인 불가 상태(승인 대기/반려/정지/탈퇴 등)는 프론트 로그인 페이지로 에러 전달
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.INCOMPLETE) {
            String message = URLEncoder.encode(
                    switch (user.getStatus()) {
                        case PENDING -> "승인 대기 중인 계정입니다.";
                        case REJECTED -> "가입이 반려된 계정입니다.";
                        case SUSPENDED -> "정지된 계정입니다.";
                        case WITHDRAWN -> "탈퇴한 계정입니다.";
                        default -> "로그인할 수 없는 계정입니다.";
                    }, StandardCharsets.UTF_8
            );
            response.sendRedirect(frontendBaseUrl + "/login?error=" + message);
            return;
        }

        // ACTIVE(기존 유저) / INCOMPLETE(추가 정보 미입력 신규 유저) 모두
        // 토큰 쿠키는 여기서 심지 않고, 1회용 코드만 발급해 프론트 콜백 페이지로 넘긴다.
        // 실제 토큰 쿠키는 프론트가 호출하는 /api/v1/auth/oauth/exchange 응답에서 발급되어
        // 프론트엔드 도메인의 first-party 쿠키로 저장된다.
        String code = oAuthCodeService.issue(user.getId());
        response.sendRedirect(frontendBaseUrl + "/oauth/callback?code=" + code);
    }
}
