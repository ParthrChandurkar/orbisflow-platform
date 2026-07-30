import type { PageResponse, RequestStatus } from "./requests";

export interface AuditEventView {
  id: string;
  event_type: string;
  actor_kind: "user" | "system";
  actor_user_id: string | null;
  previous_status: RequestStatus | null;
  resulting_status: RequestStatus | null;
  context: Record<string, unknown>;
  created_at: string;
}

export type AuditPage = PageResponse<AuditEventView>;
