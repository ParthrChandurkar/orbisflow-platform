import type { RequestDetail } from "@/lib/contracts/requests";
import { RequestStatusBadge } from "./request-status-badge";
import {
  formatAmount,
  formatDate,
} from "@/components/dashboards/request-summary-table";

export function RequestDetailCard({ request }: { request: RequestDetail }) {
  return (
    <section className="card detail-summary">
      <div>
        <span className="eyebrow">Request {request.id.slice(0, 8)}</span>
        <h1>{request.vendor ?? "Invoice request"}</h1>
        <p>Submitted {formatDate(request.submitted_at)}</p>
      </div>
      <RequestStatusBadge status={request.status} />
      <dl className="detail-grid">
        <div>
          <dt>Total amount</dt>
          <dd>{formatAmount(request.total_amount)}</dd>
        </div>
        <div>
          <dt>Last updated</dt>
          <dd>{formatDate(request.updated_at)}</dd>
        </div>
        <div>
          <dt>Current owner</dt>
          <dd>{request.current_owner_role ?? "Workflow complete"}</dd>
        </div>
        <div>
          <dt>Version</dt>
          <dd>{request.version}</dd>
        </div>
      </dl>
      {request.manager_decision?.decision === "rejected" && (
        <div className="alert error">
          <strong>Manager feedback</strong>
          <span>{request.manager_decision.rejection_reason}</span>
        </div>
      )}
      {request.processing && (
        <div className="alert success">
          Processed as <strong>{request.processing.payment_status}</strong> on{" "}
          {formatDate(request.processing.processed_at)}.
        </div>
      )}
    </section>
  );
}
