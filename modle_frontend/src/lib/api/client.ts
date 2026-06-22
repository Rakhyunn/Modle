import createClient from "openapi-fetch";
import type { paths } from "./schema";

// 브라우저에서 보내는 API 요청은 same-origin(상대경로)으로 보내 Next.js의 rewrite 프록시
// (next.config.ts)를 거쳐 백엔드로 전달한다. 이렇게 하면 백엔드가 내려주는 인증 쿠키가
// 프론트엔드 도메인의 first-party 쿠키로 저장되어, 프론트/백엔드를 서로 다른 도메인에
// 배포해도 토큰이 유지된다(BFF 패턴). 빈 문자열이면 openapi-fetch가 "/api/v1/..." 상대
// 경로를 생성한다.
//
// 단, 서버 컴포넌트(RSC/SSR)에서 이 client를 직접 쓰는 경우 상대경로는 origin이 없어
// fetch가 불가능하다. 그래서 서버 환경에서는 백엔드 절대 URL로 직접 호출한다.
// (서버에서 호출되는 곳은 모두 공개 엔드포인트이며, 인증이 필요한 SSR은 쿠키를 전달하는
//  serverClient를 사용한다.)
export const API_BASE_URL =
  typeof window === "undefined"
    ? process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
    : "";

// 브라우저에서 백엔드로 직접 전체 페이지 이동이 필요한 경우(OAuth 시작 등)에만 쓰는 절대 URL.
// 일반 REST 호출에는 사용하지 말 것 — 그러면 프록시를 우회해 쿠키가 third-party가 된다.
export const BACKEND_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export const client = createClient<paths>({
  baseUrl: API_BASE_URL,
  credentials: "include",
});

// accessToken이 만료(401)되면 refreshToken으로 재발급 후 원요청을 한 번만 재시도
// 아래 경로들은 인증이 필요 없거나(로그인/가입/이메일 인증) 재발급 대상이 아니므로(로그아웃/재발급 자체) 제외
const REISSUE_EXEMPT_PATHS = [
  "/api/v1/auth/login",
  "/api/v1/auth/logout",
  "/api/v1/auth/reissue",
  "/api/v1/auth/signup/model",
  "/api/v1/auth/signup/client",
  "/api/v1/auth/email/verify/send",
  "/api/v1/auth/email/verify/confirm",
];

let sessionExpiredHandler: (() => void) | null = null;
let ongoingReissue: Promise<boolean> | null = null;

/** refreshToken까지 만료되어 재발급이 실패했을 때 호출할 콜백을 등록 AuthProvider가 mount 시 등록 */
export function setSessionExpiredHandler(handler: (() => void) | null) {
  sessionExpiredHandler = handler;
}

export async function authenticatedFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  const request = new Request(input, { ...init, credentials: "include" });
  const response = await fetch(request.clone());

  if (response.status !== 401) {
    return response;
  }

  if (!ongoingReissue) {
    ongoingReissue = fetch(`${API_BASE_URL}/api/v1/auth/reissue`, {
      method: "POST",
      credentials: "include",
    })
      .then((r) => r.ok)
      .finally(() => {
        ongoingReissue = null;
      });
  }

  const reissueOk = await ongoingReissue;

  if (!reissueOk) {
    sessionExpiredHandler?.();
    return response;
  }

  return fetch(request);
}

// onRequest 시점에 요청 바디가 소비되기 전에 복제해두고, onResponse에서 재시도용으로 사용
const pendingRequests = new Map<string, Request>();

client.use({
  onRequest({ request, id }) {
    pendingRequests.set(id, request.clone());
  },
  async onResponse({ id, request, response }) {
    const original = pendingRequests.get(id);
    pendingRequests.delete(id);

    if (response.status !== 401) {
      return undefined;
    }

    const pathname = new URL(request.url).pathname;
    if (REISSUE_EXEMPT_PATHS.includes(pathname) || !original) {
      return undefined;
    }

    if (!ongoingReissue) {
      ongoingReissue = fetch(`${API_BASE_URL}/api/v1/auth/reissue`, {
        method: "POST",
        credentials: "include",
      })
        .then((r) => r.ok)
        .finally(() => {
          ongoingReissue = null;
        });
    }

    const reissueOk = await ongoingReissue;

    if (!reissueOk) {
      sessionExpiredHandler?.();
      return undefined;
    }

    return fetch(original);
  },
});
