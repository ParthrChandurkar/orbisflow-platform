import type { ReactNode } from "react";
import { RoleGuard } from "@/components/auth/auth-guard";

export default function EmployeeLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <RoleGuard role="employee">{children}</RoleGuard>;
}
