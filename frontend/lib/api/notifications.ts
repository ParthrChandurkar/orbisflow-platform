import { apiRequest } from "./browser-client";
import type {
  NotificationPage,
  NotificationView,
} from "@/lib/contracts/notifications";

export const getNotifications = (
  view: "recent" | "unread",
  page = 0,
  size = 20,
) =>
  apiRequest<NotificationPage>(
    `/api/v1/notifications?view=${view}&page=${page}&size=${size}`,
  );

export const markNotificationRead = (id: string) =>
  apiRequest<NotificationView>(`/api/v1/notifications/${id}/read`, {
    method: "PATCH",
  });
