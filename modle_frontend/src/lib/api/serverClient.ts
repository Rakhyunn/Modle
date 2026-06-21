import { cookies } from "next/headers";
import createClient from "openapi-fetch";
import type { paths } from "./schema";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export async function getServerClient() {
  const cookieStore = await cookies();

  const allCookies = cookieStore.getAll();
  console.log(
    "SSR 쿠키 목록:",
    allCookies.map((c) => c.name),
  );

  const cookieString = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");

  return createClient<paths>({
    baseUrl: API_BASE_URL,
    headers: {
      Cookie: cookieString,
    },
  });
}
