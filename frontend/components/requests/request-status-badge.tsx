import type { RequestStatus } from "@/lib/contracts/requests";
import {
  CircleAlert,
  CircleCheck,
  CircleDollarSign,
  Clock3,
  ClipboardCheck,
  XCircle,
} from "lucide-react";

const labels: Record<RequestStatus, string> = {
  uploaded_extracting: "Extracting",
  employee_review: "Needs correction",
  manager_review: "Manager review",
  rejected: "Rejected",
  finance_review: "Finance review",
  processed: "Processed",
};

const icons = {
  uploaded_extracting: Clock3,
  employee_review: CircleAlert,
  manager_review: ClipboardCheck,
  rejected: XCircle,
  finance_review: CircleDollarSign,
  processed: CircleCheck,
} satisfies Record<RequestStatus, typeof Clock3>;

export function RequestStatusBadge({ status }: { status: RequestStatus }) {
  const Icon = icons[status];
  return (
    <span className={`status-badge status-${status}`}>
      <Icon aria-hidden="true" size={13} strokeWidth={2.2} />
      {labels[status]}
    </span>
  );
}
