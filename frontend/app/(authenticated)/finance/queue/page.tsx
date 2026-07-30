"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { getFinanceRequests } from "@/lib/api/dashboards";
import type { PageResponse, RequestSummary } from "@/lib/contracts/requests";
import { ApiError } from "@/lib/contracts/api-error";
import { PaginatedTable } from "@/components/dashboards/paginated-table";
import { LoadingState, PageState } from "@/components/feedback/page-state";

type FinanceView = "finance_review" | "processed";

const views: Array<[FinanceView, string]> = [
  ["finance_review", "Awaiting processing"],
  ["processed", "Processed"],
];

const sortOptions = {
  finance_review: [
    ["updated_at", "Queue age"],
    ["total_amount", "Amount"],
    ["status", "Status"],
  ],
  processed: [
    ["processed_at", "Processed time"],
    ["updated_at", "Last updated"],
    ["total_amount", "Amount"],
  ],
} satisfies Record<FinanceView, string[][]>;

const defaults = {
  finance_review: { sort: "updated_at", direction: "asc" },
  processed: { sort: "processed_at", direction: "desc" },
} satisfies Record<FinanceView, { sort: string; direction: "asc" | "desc" }>;

export default function FinanceQueuePage() {
  const search = useSearchParams();
  const router = useRouter();
  const [data, setData] = useState<PageResponse<RequestSummary> | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [retry, setRetry] = useState(0);
  const queryKey = search.toString();
  const selectedView = normalizeView(search.get("status"));
  const selectedSort = search.get("sort") ?? defaults[selectedView].sort;
  const selectedDirection =
    search.get("direction") ?? defaults[selectedView].direction;

  useEffect(() => {
    let active = true;
    getFinanceRequests(new URLSearchParams(queryKey))
      .then((page) => active && setData(page))
      .catch((caught) => active && setError(caught as ApiError));
    return () => {
      active = false;
    };
  }, [queryKey, retry]);

  function update(key: string, value: string) {
    setData(null);
    setError(null);
    const next = new URLSearchParams(queryKey);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.set("page", "0");
    if (key === "status") {
      next.delete("sort");
      next.delete("direction");
    }
    router.push(`/finance/queue?${next.toString()}`);
  }

  function retryRead() {
    setData(null);
    setError(null);
    setRetry((value) => value + 1);
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">Finance workspace</span>
          <h1>Invoice processing</h1>
          <p>
            Process Manager-approved invoices and review recently completed
            payments.
          </p>
        </div>
      </div>

      <section className="card">
        <div className="toolbar">
          <label>
            View
            <select
              onChange={(event) => update("status", event.target.value)}
              value={selectedView}
            >
              {views.map(([value, label]) => (
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
              value={selectedSort}
            >
              {sortOptions[selectedView].map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Direction
            <select
              onChange={(event) => update("direction", event.target.value)}
              value={selectedDirection}
            >
              <option value="asc">Oldest first</option>
              <option value="desc">Newest first</option>
            </select>
          </label>
        </div>

        {!data && !error && <LoadingState label="Loading Finance queue…" />}
        {error && (
          <PageState
            title="We couldn’t load the Finance queue"
            message={`${error.message}${
              error.correlationId ? ` · Reference ${error.correlationId}` : ""
            }`}
            action={
              <button className="button" onClick={retryRead} type="button">
                Try again
              </button>
            }
          />
        )}
        {data?.items.length === 0 && (
          <PageState
            title={
              selectedView === "finance_review"
                ? "No invoices awaiting processing"
                : "No processed invoices"
            }
            message={
              selectedView === "finance_review"
                ? "Manager-approved invoices will appear here."
                : "Processed invoices will appear here after Finance completes them."
            }
          />
        )}
        {data && data.items.length > 0 && (
          <PaginatedTable
            data={data}
            onPageChange={(page) => update("page", String(page))}
            requestBasePath="/finance/requests"
          />
        )}
      </section>
    </>
  );
}

function normalizeView(value: string | null): FinanceView {
  return value === "processed" ? "processed" : "finance_review";
}
