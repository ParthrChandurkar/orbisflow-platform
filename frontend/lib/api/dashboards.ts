import { apiRequest } from "./browser-client";
import type { PageResponse, RequestSummary } from "@/lib/contracts/requests";

export function getEmployeeRequests(query: URLSearchParams) {
  return apiRequest<PageResponse<RequestSummary>>(
    `/api/v1/dashboards/employee/requests?${query.toString()}`,
  );
}
