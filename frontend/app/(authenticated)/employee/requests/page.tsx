"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { getEmployeeRequests } from "@/lib/api/dashboards";
import type { PageResponse, RequestSummary } from "@/lib/contracts/requests";
import { RequestSummaryTable } from "@/components/dashboards/request-summary-table";
import { LoadingState, PageState } from "@/components/feedback/page-state";
import { ApiError } from "@/lib/contracts/api-error";

const statuses = [
  ["", "All statuses"],
  ["uploaded_extracting", "Extracting"],
  ["employee_review", "Needs correction"],
  ["manager_review", "Manager review"],
  ["rejected", "Rejected"],
  ["finance_review", "Finance review"],
  ["processed", "Processed"],
];

export default function EmployeeRequestsPage() {
  const search = useSearchParams();
  const router = useRouter();
  const [data, setData] = useState<PageResponse<RequestSummary> | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const queryKey = search.toString();

  useEffect(() => {
    let active = true;
    getEmployeeRequests(new URLSearchParams(queryKey))
      .then((page) => active && setData(page))
      .catch((caught) => active && setError(caught as ApiError));
    return () => {
      active = false;
    };
  }, [queryKey]);

  function update(key: string, value: string) {
    setData(null);
    setError(null);
    const next = new URLSearchParams(queryKey);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.set("page", "0");
    router.push(`/employee/requests?${next.toString()}`);
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">Employee workspace</span>
          <h1>My invoice requests</h1>
          <p>Track extraction, approval, and payment progress.</p>
        </div>
        <Link className="button primary" href="/employee/requests/new">
          + Submit invoice
        </Link>
      </div>
      <section className="card">
        <div className="toolbar">
          <label>
            Status
            <select
              onChange={(event) => update("status", event.target.value)}
              value={search.get("status") ?? ""}
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
              value={search.get("sort") ?? "submitted_at"}
            >
              <option value="submitted_at">Submitted</option>
              <option value="updated_at">Last updated</option>
              <option value="total_amount">Amount</option>
              <option value="status">Status</option>
            </select>
          </label>
          <label>
            Direction
            <select
              onChange={(event) => update("direction", event.target.value)}
              value={search.get("direction") ?? "desc"}
            >
              <option value="desc">Newest first</option>
              <option value="asc">Oldest first</option>
            </select>
          </label>
        </div>
        {!data && !error && <LoadingState label="Loading your requests…" />}
        {error && (
          <PageState
            title="We couldn’t load your requests"
            message={`${error.message}${error.correlationId ? ` · Reference ${error.correlationId}` : ""}`}
            action={
              <button className="button" onClick={() => router.refresh()}>
                Try again
              </button>
            }
          />
        )}
        {data?.items.length === 0 && (
          <PageState
            title="No requests yet"
            message="Submit your first invoice to start the approval flow."
            action={
              <Link className="button primary" href="/employee/requests/new">
                Submit invoice
              </Link>
            }
          />
        )}
        {data && data.items.length > 0 && (
          <>
            <RequestSummaryTable items={data.items} />
            <div className="pagination">
              <span>
                Page {data.page + 1} of {Math.max(data.total_pages, 1)} ·{" "}
                {data.total_elements} requests
              </span>
              <div>
                <button
                  className="button small"
                  disabled={data.page === 0}
                  onClick={() => update("page", String(data.page - 1))}
                >
                  Previous
                </button>
                <button
                  className="button small"
                  disabled={data.page + 1 >= data.total_pages}
                  onClick={() => update("page", String(data.page + 1))}
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
