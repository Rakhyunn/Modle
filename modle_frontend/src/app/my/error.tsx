"use client";

import Link from "next/link";
import { useEffect } from "react";

// /my 하위 페이지(프로필, 프로필 수정 등)의 서버/클라이언트 오류를 처리하는 바운더리.
// 사용자에게는 친근한 안내만 노출하고, 실제 오류 메시지·코드는 화면에 띄우지 않는다.
// (디버깅용 상세 정보는 콘솔/서버 로그로만 남기고, 개발 환경에서만 화면에 추가로 표시)
export default function MyError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const isDev = process.env.NODE_ENV === "development";

  useEffect(() => {
    // 화면에는 안 띄우더라도 콘솔/서버 로그에는 남겨 분석을 돕는다
    console.error("[my] 페이지 오류:", error);
  }, [error]);

  return (
    <main className="flex flex-1 items-center justify-center bg-canvas px-4 py-16 text-ink">
      <div className="flex w-full max-w-[440px] flex-col items-center text-center">
        <h1 className="text-[20px] font-bold leading-7">
          잠시 후 다시 시도해주세요
        </h1>
        <p className="mt-3 text-[15px] leading-6 text-body">
          일시적인 문제로 페이지를 불러오지 못했어요.
          <br />
          잠시 후 다시 시도해주세요.
        </p>

        <div className="mt-6 flex gap-3">
          <button
            type="button"
            onClick={reset}
            className="inline-flex h-11 items-center justify-center rounded-md bg-primary px-6 text-[15px] font-semibold leading-6 text-on-primary transition hover:bg-primary-hover"
          >
            다시 시도
          </button>
          <Link
            href="/"
            className="inline-flex h-11 items-center justify-center rounded-md border border-hairline px-6 text-[15px] font-semibold leading-6 text-ink transition hover:bg-gray-50"
          >
            홈으로
          </Link>
        </div>

        {/* 개발 환경에서만 실제 오류 내용을 표시 — 프로덕션 사용자에게는 노출되지 않음 */}
        {isDev ? (
          <pre className="mt-8 w-full overflow-auto rounded-md bg-gray-50 p-3 text-left text-[12px] leading-5 text-mute">
            {error.message}
            {error.digest ? `\ndigest: ${error.digest}` : ""}
          </pre>
        ) : null}
      </div>
    </main>
  );
}
