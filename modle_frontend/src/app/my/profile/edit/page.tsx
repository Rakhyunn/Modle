import { ModelEditForm } from "@/components/model/ModelEditForm";
import { ClientEditForm } from "@/components/profile/ClientEditForm";
import { getMyClient } from "@/lib/api/clientProfile";
import { getMyModel } from "@/lib/api/model";
import { getServerClient } from "@/lib/api/serverClient";
import { redirect } from "next/navigation";

export const dynamic = "force-dynamic";

export const metadata = {
  title: "내 프로필 수정 | 모들",
};

async function getProfileEditPageData() {
  const serverClient = await getServerClient();

  const { data: meData, error, response } = await serverClient.GET(
    "/api/v1/auth/me",
  );
  const status = response?.status;

  // 인증 만료/미로그인은 404가 아니라 로그인 페이지로 보낸다
  if (status === 401) {
    redirect("/login?redirect=/my/profile/edit");
  }
  // 그 외 오류는 상태코드를 담아 그대로 던져 error.tsx 바운더리가 표시하게 한다
  if (error) {
    throw new Error(`내 정보 조회 실패 (status ${status ?? "unknown"})`);
  }

  const user = meData?.data;
  if (!user) throw new Error("사용자 정보가 비어 있습니다.");

  if (user.role === "CLIENT") {
    return {
      role: "CLIENT" as const,
      data: await getMyClient(serverClient),
    };
  }

  return {
    role: "MODEL" as const,
    data: await getMyModel(serverClient),
  };
}

export default async function MyProfileEditPage() {
  // 인증 오류는 redirect로, 그 외 오류는 my/error.tsx 바운더리로 처리된다
  const pageData = await getProfileEditPageData();

  return (
    <main className="max-w-[1200px] mx-auto px-6 py-12">
      {pageData.role === "CLIENT" ? (
        <ClientEditForm initialData={pageData.data} />
      ) : (
        <ModelEditForm initialData={pageData.data} />
      )}
    </main>
  );
}
