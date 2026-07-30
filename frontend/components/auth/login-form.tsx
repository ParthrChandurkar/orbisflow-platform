"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { getCurrentUser, login } from "@/lib/api/auth";
import { ApiError } from "@/lib/contracts/api-error";
import { roleHome } from "@/lib/auth/role-routes";

export function LoginForm() {
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    getCurrentUser(false)
      .then((user) => router.replace(roleHome(user.role)))
      .catch(() => undefined);
  }, [router]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const user = await login(identifier.trim(), password);
      const requested = searchParams.get("returnTo");
      const safeReturn =
        requested?.startsWith("/") && !requested.startsWith("//")
          ? requested
          : null;
      router.replace(safeReturn ?? roleHome(user.role));
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "Unable to sign in right now.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={submit}>
      <label>
        Login identifier
        <input
          autoComplete="username"
          value={identifier}
          onChange={(event) => setIdentifier(event.target.value)}
          required
          placeholder="employee1"
        />
      </label>
      <label>
        Password
        <input
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
      </label>
      {error && <div className="alert error">{error}</div>}
      <button className="button primary" disabled={submitting} type="submit">
        {submitting ? "Signing in…" : "Sign in"}
      </button>
    </form>
  );
}
