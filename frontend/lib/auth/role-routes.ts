import type { UserRole } from "@/lib/contracts/auth";

export function roleHome(role: UserRole): string {
  if (role === "employee") return "/employee/requests";
  if (role === "manager") return "/manager/queue";
  return "/finance/queue";
}

export function requestRoute(role: UserRole, requestId: string): string {
  return `/${role}/requests/${requestId}`;
}
