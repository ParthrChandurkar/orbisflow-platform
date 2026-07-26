import type { ReactNode } from "react";

export function AuthGuard({ children }: Readonly<{ children: ReactNode }>) {
  return <>{children}</>;
}
