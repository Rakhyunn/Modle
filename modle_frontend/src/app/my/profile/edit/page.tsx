import { ModelEditForm } from "@/components/model/ModelEditForm";
import { ClientEditForm } from "@/components/profile/ClientEditForm";
import { getMyClient } from "@/lib/api/clientProfile";
import { getMyModel } from "@/lib/api/model";
import { getServerClient } from "@/lib/api/serverClient";
import { notFound } from "next/navigation";

export const dynamic = "force-dynamic";

export const metadata = {
  title: "내 프로필 수정 | 모들",
};

async function getProfileEditPageData() {
  const serverClient = await getServerClient();

  const { data: meData, error } = await serverClient.GET("/api/v1/auth/me");
  if (error) throw new Error("로그인 후 이용해주세요.");

  const user = meData?.data;
  if (!user) throw new Error("사용자 정보가 올바르지 않습니다.");

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
  let pageData: Awaited<ReturnType<typeof getProfileEditPageData>>;

  try {
    pageData = await getProfileEditPageData();
  } catch (error) {
    console.error("내 프로필 로딩 실패:", error);
    notFound();
  }

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
