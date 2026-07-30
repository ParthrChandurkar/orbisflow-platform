import type { PageResponse } from "./requests";

export type NotificationType =
  | "employee_correction"
  | "employee_rejection"
  | "manager_assignment"
  | "finance_assignment"
  | "processed";

export interface NotificationView {
  id: string;
  request_id: string;
  type: NotificationType;
  read_at: string | null;
  created_at: string;
}

export type NotificationPage = PageResponse<NotificationView>;
