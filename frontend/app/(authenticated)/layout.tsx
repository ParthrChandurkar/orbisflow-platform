import type { ReactNode } from "react";

export default function AuthenticatedLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <>{children}</>;
}
