"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  getNotifications,
  markNotificationRead,
} from "@/lib/api/notifications";
import type { NotificationPage } from "@/lib/contracts/notifications";
import { ApiError } from "@/lib/contracts/api-error";
import { LoadingState, PageState } from "@/components/feedback/page-state";
import { NotificationList } from "@/components/notifications/notification-list";

export default function NotificationsPage() {
  const search = useSearchParams();
  const router = useRouter();
  const view = search.get("view") === "unread" ? "unread" : "recent";
  const pageNumber = Math.max(0, Number(search.get("page") ?? "0") || 0);
  const [data, setData] = useState<NotificationPage | null>(null);
  const [error, setError] = useState<ApiError | null>(null);

  async function load() {
    setData(null);
    setError(null);
    try {
      setData(await getNotifications(view, pageNumber, 20));
    } catch (caught) {
      setError(caught as ApiError);
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
    // load is deliberately keyed only by URL state.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, pageNumber]);

  function navigate(nextView: "recent" | "unread", nextPage = 0) {
    setData(null);
    setError(null);
    router.push(`/notifications?view=${nextView}&page=${nextPage}`);
  }

  async function markRead(id: string) {
    try {
      await markNotificationRead(id);
      await load();
    } catch (caught) {
      setError(caught as ApiError);
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">Inbox</span>
          <h1>Notifications</h1>
          <p>Updates from your invoice workflow, scoped to your account.</p>
        </div>
      </div>
      <section className="card">
        <div className="segmented-control">
          <button
            className={view === "recent" ? "active" : ""}
            onClick={() => navigate("recent")}
          >
            Recent
          </button>
          <button
            className={view === "unread" ? "active" : ""}
            onClick={() => navigate("unread")}
          >
            Unread
          </button>
        </div>
        {!data && !error && <LoadingState label="Loading notifications…" />}
        {error && (
          <PageState
            title="Unable to load notifications"
            message={error.message}
            action={
              <button className="button" onClick={() => void load()}>
                Try again
              </button>
            }
          />
        )}
        {data?.items.length === 0 && (
          <PageState
            title={view === "unread" ? "You’re all caught up" : "No recent notifications"}
            message={
              view === "unread"
                ? "There are no unread workflow updates."
                : "New workflow updates will appear here."
            }
          />
        )}
        {data && data.items.length > 0 && (
          <>
            <NotificationList onRead={markRead} page={data} />
            <div className="pagination">
              <span>
                Page {data.page + 1} of {Math.max(data.total_pages, 1)}
              </span>
              <div>
                <button
                  className="button small"
                  disabled={data.page === 0}
                  onClick={() => navigate(view, data.page - 1)}
                >
                  Previous
                </button>
                <button
                  className="button small"
                  disabled={data.page + 1 >= data.total_pages}
                  onClick={() => navigate(view, data.page + 1)}
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </section>
    </>
  );
}
