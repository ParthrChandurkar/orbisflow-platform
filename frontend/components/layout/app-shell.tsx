"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { logout } from "@/lib/api/auth";
import { useAuth } from "@/components/auth/auth-guard";
import { NotificationBell } from "@/components/notifications/notification-bell";
import { roleHome } from "@/lib/auth/role-routes";

export function AppShell({ children }: Readonly<{ children: ReactNode }>) {
  const user = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const employeeLinks = [
    { href: "/employee/requests", label: "My requests" },
    { href: "/employee/requests/new", label: "Submit invoice" },
  ];
  const links =
    user.role === "employee"
      ? employeeLinks
      : [
          {
            href: roleHome(user.role),
            label: user.role === "manager" ? "Approval queue" : "Finance queue",
          },
        ];

  async function signOut() {
    try {
      await logout();
    } finally {
      router.replace("/login");
    }
  }

  return (
    <div className="app-frame">
      <aside className="sidebar">
        <Link className="brand" href={roleHome(user.role)}>
          <span className="brand-mark">O</span>
          <span>Orbis Flow</span>
        </Link>
        <nav aria-label="Primary">
          {links.map((link) => (
            <Link
              className={
                pathname === link.href ||
                (link.href.endsWith("/requests") &&
                  pathname.startsWith(`${link.href}/`) &&
                  !pathname.endsWith("/new"))
                  ? "nav-link active"
                  : "nav-link"
              }
              href={link.href}
              key={link.href}
            >
              {link.label}
            </Link>
          ))}
          <Link
            className={
              pathname === "/notifications" ? "nav-link active" : "nav-link"
            }
            href="/notifications"
          >
            Notifications
          </Link>
        </nav>
        <div className="sidebar-footer">
          <div>
            <strong>{user.login_identifier}</strong>
            <span className="role-label">{user.role}</span>
          </div>
          <button className="text-button" onClick={signOut} type="button">
            Log out
          </button>
        </div>
      </aside>
      <div className="app-content">
        <header className="topbar">
          <div>
            <span className="eyebrow">Invoice operations</span>
          </div>
          <NotificationBell />
        </header>
        <main className="page-container">{children}</main>
      </div>
    </div>
  );
}
