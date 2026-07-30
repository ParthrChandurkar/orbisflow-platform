import Link from "next/link";
import type { RequestSummary } from "@/lib/contracts/requests";
import { RequestStatusBadge } from "@/components/requests/request-status-badge";

export function RequestSummaryTable({
  items,
  requestBasePath = "/employee/requests",
}: {
  items: RequestSummary[];
  requestBasePath?: string;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Invoice</th>
            <th>Status</th>
            <th>Amount</th>
            <th>Submitted</th>
            <th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {items.map((request) => (
            <tr key={request.id}>
              <td>
                <strong>{request.vendor ?? "Extraction pending"}</strong>
                <span className="table-subtitle">
                  {request.id.slice(0, 8).toUpperCase()}
                </span>
              </td>
              <td>
                <RequestStatusBadge status={request.status} />
              </td>
              <td>{formatAmount(request.total_amount)}</td>
              <td>{formatDate(request.submitted_at)}</td>
              <td>
                <Link
                  className="row-link"
                  href={`${requestBasePath}/${request.id}`}
                >
                  View →
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function formatAmount(value: string | null): string {
  if (!value) return "—";
  return new Intl.NumberFormat("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(Number(value));
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
