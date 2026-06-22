package com.modle.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

// 소셜 로그인 성공 후 프론트엔드가 전달받은 1회용 코드를 토큰으로 교환할 때 보내는 요청.
public record OAuthExchangeRequest(
        @NotBlank
        String code
) {
}
