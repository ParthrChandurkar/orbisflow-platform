import type { ReactNode } from "react";
import { RoleGuard } from "@/components/auth/auth-guard";

export default function ManagerLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <RoleGuard role="manager">{children}</RoleGuard>;
}
