"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  getManagerRequests,
  getManagerTeamActivity,
} from "@/lib/api/dashboards";
import type { TeamActivity } from "@/lib/contracts/dashboards";
import type { PageResponse, RequestSummary } from "@/lib/contracts/requests";
import { ApiError } from "@/lib/contracts/api-error";
import { TeamActivityCards } from "@/components/dashboards/team-activity-cards";
import { PaginatedTable } from "@/components/dashboards/paginated-table";
import { LoadingState, PageState } from "@/components/feedback/page-state";

const statuses = [
  ["manager_review", "Awaiting review"],
  ["rejected", "Rejected"],
  ["finance_review", "With Finance"],
  ["processed", "Processed"],
];

export default function ManagerQueuePage() {
  const search = useSearchParams();
  const router = useRouter();
  const [data, setData] = useState<PageResponse<RequestSummary> | null>(null);
  const [activity, setActivity] = useState<TeamActivity | null>(null);
  const [queueError, setQueueError] = useState<ApiError | null>(null);
  const [activityError, setActivityError] = useState<ApiError | null>(null);
  const [retry, setRetry] = useState(0);
  const queryKey = search.toString();
  const selectedStatus = search.get("status") ?? "manager_review";

  useEffect(() => {
    let active = true;
    getManagerRequests(new URLSearchParams(queryKey))
      .then((page) => active && setData(page))
      .catch((caught) => active && setQueueError(caught as ApiError));
    return () => {
      active = false;
    };
  }, [queryKey, retry]);

  useEffect(() => {
    let active = true;
    getManagerTeamActivity()
      .then((result) => active && setActivity(result))
      .catch((caught) => active && setActivityError(caught as ApiError));
    return () => {
      active = false;
    };
  }, [retry]);

  function update(key: string, value: string) {
    setData(null);
    setQueueError(null);
    const next = new URLSearchParams(queryKey);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.set("page", "0");
    router.push(`/manager/queue?${next.toString()}`);
  }

  function retryReads() {
    setData(null);
    setActivity(null);
    setQueueError(null);
    setActivityError(null);
    setRetry((value) => value + 1);
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">Manager workspace</span>
          <h1>Approval queue</h1>
          <p>Review assigned invoices and monitor your team’s activity.</p>
        </div>
      </div>

      {!activity && !activityError && (
        <section className="activity-grid" aria-label="Loading team activity">
          {[0, 1, 2].map((item) => (
            <div className="activity-card skeleton-card" key={item} />
          ))}
        </section>
      )}
      {activity && <TeamActivityCards activity={activity} />}
      {activityError && (
        <div className="alert error">
          <span>{activityError.message}</span>
          <button className="text-button" onClick={retryReads} type="button">
            Retry team activity
          </button>
        </div>
      )}

      <section className="card">
        <div className="toolbar">
          <label>
            Status
            <select
              onChange={(event) => update("status", event.target.value)}
              value={selectedStatus}
            >
              {statuses.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Sort
            <select
              onChange={(event) => update("sort", event.target.value)}
              value={search.get("sort") ?? "updated_at"}
            >
              <option value="updated_at">Last updated</option>
              <option value="submitted_at">Submitted</option>
              <option value="total_amount">Amount</option>
              <option value="status">Status</option>
            </select>
          </label>
          <label>
            Direction
            <select
              onChange={(event) => update("direction", event.target.value)}
              value={search.get("direction") ?? "asc"}
            >
              <option value="asc">Oldest first</option>
              <option value="desc">Newest first</option>
            </select>
          </label>
        </div>

        {!data && !queueError && <LoadingState label="Loading approval queue…" />}
        {queueError && (
          <PageState
            title="We couldn’t load the approval queue"
            message={`${queueError.message}${
              queueError.correlationId
                ? ` · Reference ${queueError.correlationId}`
                : ""
            }`}
            action={
              <button className="button" onClick={retryReads} type="button">
                Try again
              </button>
            }
          />
        )}
        {data?.items.length === 0 && (
          <PageState
            title={
              selectedStatus === "manager_review"
                ? "No requests awaiting review"
                : `No ${statuses
                    .find(([value]) => value === selectedStatus)?.[1]
                    .toLowerCase()} requests`
            }
            message={
              selectedStatus === "manager_review"
                ? "Newly routed invoices will appear here."
                : "Choose another status to view assigned requests."
            }
          />
        )}
        {data && data.items.length > 0 && (
          <PaginatedTable
            data={data}
            onPageChange={(page) => update("page", String(page))}
            requestBasePath="/manager/requests"
          />
        )}
      </section>
    </>
  );
}
