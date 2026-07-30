import { ApiError, parseApiError } from "@/lib/contracts/api-error";

export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export interface ApiRequestOptions extends RequestInit {
  redirectOn401?: boolean;
}

function cookieValue(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  const prefix = `${name}=`;
  return document.cookie
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length);
}

function isMutation(method?: string): boolean {
  return ["POST", "PATCH", "PUT", "DELETE"].includes(
    (method ?? "GET").toUpperCase(),
  );
}

export function redirectToLogin(): void {
  if (typeof window === "undefined" || window.location.pathname === "/login") {
    return;
  }
  const returnTo = `${window.location.pathname}${window.location.search}`;
  window.location.assign(`/login?returnTo=${encodeURIComponent(returnTo)}`);
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { redirectOn401 = true, ...requestOptions } = options;
  const headers = new Headers(requestOptions.headers);
  if (
    requestOptions.body &&
    !(requestOptions.body instanceof FormData) &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json");
  }
  if (isMutation(requestOptions.method)) {
    const token = cookieValue("XSRF-TOKEN");
    if (token) headers.set("X-XSRF-TOKEN", decodeURIComponent(token));
  }
  const response = await fetch(`${API_BASE}${path}`, {
    ...requestOptions,
    headers,
    credentials: "include",
    cache: "no-store",
  });
  if (!response.ok) {
    const value = await response.json().catch(() => null);
    const error = parseApiError(response.status, value);
    if (response.status === 401 && redirectOn401) redirectToLogin();
    throw error;
  }
  if (response.status === 204 || response.headers.get("content-length") === "0") {
    return undefined as T;
  }
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export function absoluteApiUrl(path: string): string {
  return path.startsWith("http") ? path : `${API_BASE}${path}`;
}

export interface UploadOptions {
  path: string;
  formData: FormData;
  onProgress?: (percentage: number) => void;
}

export function uploadMultipart<T>({
  path,
  formData,
  onProgress,
}: UploadOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("POST", `${API_BASE}${path}`);
    request.withCredentials = true;
    const token = cookieValue("XSRF-TOKEN");
    if (token) request.setRequestHeader("X-XSRF-TOKEN", decodeURIComponent(token));
    request.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable) {
        onProgress?.(Math.round((event.loaded / event.total) * 100));
      }
    });
    request.addEventListener("load", () => {
      const value = request.responseText
        ? safeJson(request.responseText)
        : undefined;
      if (request.status >= 200 && request.status < 300) {
        resolve(value as T);
        return;
      }
      const error = parseApiError(request.status, value);
      if (request.status === 401) redirectToLogin();
      reject(error);
    });
    request.addEventListener("error", () =>
      reject(new ApiError(0, "NETWORK_ERROR", "Unable to reach Orbis Flow.")),
    );
    request.send(formData);
  });
}

function safeJson(value: string): unknown {
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}
