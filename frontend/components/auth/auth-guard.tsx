"use client";

import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { getCurrentUser } from "@/lib/api/auth";
import type { UserRole, UserView } from "@/lib/contracts/auth";
import { roleHome } from "@/lib/auth/role-routes";

const AuthContext = createContext<UserView | null>(null);

export function useAuth(): UserView {
  const user = useContext(AuthContext);
  if (!user) throw new Error("useAuth must be used inside AuthGuard");
  return user;
}

export function AuthGuard({ children }: Readonly<{ children: ReactNode }>) {
  const [user, setUser] = useState<UserView | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let active = true;
    getCurrentUser()
      .then((current) => {
        if (active) setUser(current);
      })
      .catch(() => {
        if (active) setError(true);
      });
    return () => {
      active = false;
    };
  }, []);

  if (!user && !error) {
    return (
      <main className="centered-page" aria-live="polite">
        <div className="spinner" />
        <p>Checking your session…</p>
      </main>
    );
  }
  if (error) {
    return (
      <main className="centered-page">
        <p>Redirecting to sign in…</p>
      </main>
    );
  }
  return <AuthContext.Provider value={user}>{children}</AuthContext.Provider>;
}

export function RoleGuard({
  role,
  children,
}: Readonly<{ role: UserRole; children: ReactNode }>) {
  const user = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (user.role !== role) router.replace(roleHome(user.role));
  }, [pathname, role, router, user.role]);

  if (user.role !== role) return null;
  return children;
}
