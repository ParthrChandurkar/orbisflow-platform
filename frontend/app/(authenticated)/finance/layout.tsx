import type { ReactNode } from "react";
import { RoleGuard } from "@/components/auth/auth-guard";

export default function FinanceLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <RoleGuard role="finance">{children}</RoleGuard>;
}
