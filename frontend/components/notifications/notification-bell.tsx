"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getNotifications } from "@/lib/api/notifications";

export function NotificationBell() {
  const [count, setCount] = useState(0);

  useEffect(() => {
    getNotifications("unread", 0, 1)
      .then((page) => setCount(page.total_elements))
      .catch(() => undefined);
  }, []);

  return (
    <Link className="notification-bell" href="/notifications" aria-label="Notifications">
      <span>◔</span>
      {count > 0 && <b>{count > 99 ? "99+" : count}</b>}
    </Link>
  );
}
