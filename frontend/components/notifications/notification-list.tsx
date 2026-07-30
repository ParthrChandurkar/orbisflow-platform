"use client";

import Link from "next/link";
import type { NotificationPage } from "@/lib/contracts/notifications";
import { requestRoute } from "@/lib/auth/role-routes";
import { useAuth } from "@/components/auth/auth-guard";
import { formatDate } from "@/components/dashboards/request-summary-table";

const labels = {
  employee_correction: "Invoice details need correction",
  employee_rejection: "Manager returned your invoice",
  manager_assignment: "Invoice ready for Manager review",
  finance_assignment: "Invoice ready for Finance",
  processed: "Invoice processing completed",
};

export function NotificationList({
  page,
  onRead,
}: {
  page: NotificationPage;
  onRead: (id: string) => Promise<void>;
}) {
  const user = useAuth();
  return (
    <div className="notification-list">
      {page.items.map((notification) => (
        <article
          className={notification.read_at ? "notification-item" : "notification-item unread"}
          key={notification.id}
        >
          <span className="notification-dot" />
          <div>
            <strong>{labels[notification.type]}</strong>
            <p>Request {notification.request_id.slice(0, 8).toUpperCase()}</p>
            <small>{formatDate(notification.created_at)}</small>
          </div>
          <div className="notification-actions">
            {!notification.read_at && (
              <button
                className="text-button"
                onClick={() => void onRead(notification.id)}
              >
                Mark read
              </button>
            )}
            <Link href={requestRoute(user.role, notification.request_id)}>
              Open request →
            </Link>
          </div>
        </article>
      ))}
    </div>
  );
}
