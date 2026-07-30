import { apiRequest } from "./browser-client";
import type { AuditPage } from "@/lib/contracts/audit";

export const getAudit = (requestId: string, page = 0, size = 50) =>
  apiRequest<AuditPage>(
    `/api/v1/requests/${requestId}/audit?page=${page}&size=${size}`,
  );
