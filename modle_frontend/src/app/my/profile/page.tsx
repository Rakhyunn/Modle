import { MyClientProfileContainer } from "@/components/profile/MyClientProfileContainer";
import { MyProfileContainer } from "@/components/profile/MyProfileContainer";
import { getMyClient } from "@/lib/api/clientProfile";
import { getMyModel } from "@/lib/api/model";
import { getServerClient } from "@/lib/api/serverClient";
import { notFound } from "next/navigation";

export const dynamic = "force-dynamic";

export const metadata = {
  title: "내 프로필 | 모들",
};

async function getProfilePageData() {
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

export default async function MyProfilePage() {
  let pageData: Awaited<ReturnType<typeof getProfilePageData>>;

  try {
    pageData = await getProfilePageData();
  } catch (error) {
    console.error("내 프로필 로딩 실패:", error);
    notFound();
  }

  if (pageData.role === "CLIENT") {
    return <MyClientProfileContainer initialData={pageData.data} />;
  }

  return <MyProfileContainer initialData={pageData.data} />;
}
