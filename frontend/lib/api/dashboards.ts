import { apiRequest } from "./browser-client";
import type { TeamActivity } from "@/lib/contracts/dashboards";
import type { PageResponse, RequestSummary } from "@/lib/contracts/requests";

export function getEmployeeRequests(query: URLSearchParams) {
  return apiRequest<PageResponse<RequestSummary>>(
    `/api/v1/dashboards/employee/requests?${query.toString()}`,
  );
}

export function getManagerRequests(query: URLSearchParams) {
  return apiRequest<PageResponse<RequestSummary>>(
    `/api/v1/dashboards/manager/requests?${query.toString()}`,
  );
}

export function getManagerTeamActivity() {
  return apiRequest<TeamActivity>(
    "/api/v1/dashboards/manager/team-activity",
  );
}

export function getFinanceRequests(query: URLSearchParams) {
  return apiRequest<PageResponse<RequestSummary>>(
    `/api/v1/dashboards/finance/requests?${query.toString()}`,
  );
}
