import type { RequestStatus } from "@/lib/contracts/requests";

const labels: Record<RequestStatus, string> = {
  uploaded_extracting: "Extracting",
  employee_review: "Needs correction",
  manager_review: "Manager review",
  rejected: "Rejected",
  finance_review: "Finance review",
  processed: "Processed",
};

export function RequestStatusBadge({ status }: { status: RequestStatus }) {
  return (
    <span className={`status-badge status-${status}`}>{labels[status]}</span>
  );
}
