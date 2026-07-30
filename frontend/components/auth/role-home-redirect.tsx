"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { getCurrentUser } from "@/lib/api/auth";
import { roleHome } from "@/lib/auth/role-routes";

export function RoleHomeRedirect() {
  const router = useRouter();

  useEffect(() => {
    getCurrentUser(false)
      .then((user) => router.replace(roleHome(user.role)))
      .catch(() => router.replace("/login"));
  }, [router]);

  return (
    <main className="centered-page">
      <div className="spinner" />
      <p>Opening Orbis Flow…</p>
    </main>
  );
}
