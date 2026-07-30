import { apiRequest } from "./browser-client";
import type { UserView } from "@/lib/contracts/auth";

export function login(loginIdentifier: string, password: string) {
  return apiRequest<UserView>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({
      login_identifier: loginIdentifier,
      password,
    }),
    redirectOn401: false,
  });
}

export function logout() {
  return apiRequest<void>("/api/v1/auth/logout", { method: "POST" });
}

export function getCurrentUser(redirectOn401 = true) {
  return apiRequest<UserView>("/api/v1/users/me", { redirectOn401 });
}
