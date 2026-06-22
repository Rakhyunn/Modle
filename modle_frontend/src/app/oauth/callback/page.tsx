"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";

import { useAuth } from "@/hooks/useAuth";
import type { AuthUser } from "@/types/auth";

// 소셜 로그인 성공 후 백엔드가 1회용 code와 함께 이 페이지로 리다이렉트한다.
// 여기서 code를 same-origin 프록시(/api/v1/auth/oauth/exchange)로 교환하면,
// 그 응답의 Set-Cookie가 프론트엔드 도메인의 first-party 쿠키로 저장된다.
export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={<CallbackShell message="로그인 처리 중입니다…" />}>
      <OAuthCallback />
    </Suspense>
  );
}

function OAuthCallback() {
  const router = useRouter();
  const { login } = useAuth();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  // code는 1회용이라 StrictMode의 effect 중복 실행으로 교환을 두 번 시도하면 실패한다. 1회만 실행되도록 가드.
  const exchanged = useRef(false);

  useEffect(() => {
    if (exchanged.current) {
      return;
    }
    exchanged.current = true;

    const code = searchParams.get("code");
    if (!code) {
      router.replace(
        "/login?error=" + encodeURIComponent("소셜 로그인에 실패했습니다."),
      );
      return;
    }

    (async () => {
      try {
        const res = await fetch("/api/v1/auth/oauth/exchange", {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ code }),
        });

        if (!res.ok) {
          throw new Error("exchange failed");
        }

        const json = await res.json();
        const item = json?.data?.item as
          | { id?: number; role?: AuthUser["role"] | null; status?: string }
          | undefined;

        if (!item) {
          throw new Error("invalid response");
        }

        // 추가 정보 미입력 신규 유저 — 임시 토큰 쿠키가 설정된 상태로 추가정보 페이지로 이동
        if (item.status === "INCOMPLETE") {
          router.replace("/signup/additional");
          return;
        }

        if (!item.role) {
          throw new Error("missing role");
        }

        login({ id: item.id, role: item.role });
        router.replace("/");
      } catch {
        setError("소셜 로그인 처리에 실패했습니다. 다시 시도해주세요.");
      }
    })();
  }, [searchParams, router, login]);

  if (error) {
    return (
      <CallbackShell message={error}>
        <Link
          href="/login"
          className="mt-4 inline-flex h-11 items-center justify-center rounded-md bg-primary px-6 text-[15px] font-semibold leading-6 text-on-primary transition hover:bg-primary-hover"
        >
          로그인 페이지로 돌아가기
        </Link>
      </CallbackShell>
    );
  }

  return <CallbackShell message="로그인 처리 중입니다…" />;
}

function CallbackShell({
  message,
  children,
}: {
  message: string;
  children?: React.ReactNode;
}) {
  return (
    <main className="flex flex-1 items-center justify-center bg-canvas px-4 py-12 text-ink">
      <div className="flex w-full max-w-[400px] flex-col items-center text-center">
        <p className="text-[15px] leading-6 text-body" aria-live="polite">
          {message}
        </p>
        {children}
      </div>
    </main>
  );
}
