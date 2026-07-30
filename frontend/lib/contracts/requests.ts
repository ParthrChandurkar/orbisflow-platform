export type RequestStatus =
  | "uploaded_extracting"
  | "employee_review"
  | "manager_review"
  | "rejected"
  | "finance_review"
  | "processed";

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
  sort: { field: string; direction: "asc" | "desc" };
}

export interface ValidationFlag {
  code: string;
  field: string | null;
  message: string;
}

export interface LineItem {
  line_number: number;
  description: string;
  amount: string;
}

export interface ExtractionView {
  status: "pending" | "succeeded" | "failed";
  schema_version: string;
  vendor: string | null;
  total_amount: string | null;
  invoice_date: string | null;
  line_items: LineItem[];
  validation_flags: ValidationFlag[];
  failure_category: string | null;
}

export interface DocumentView {
  id: string;
  original_filename: string;
  mime_type: string;
  file_size_bytes: number;
  created_at: string;
}

export interface RequestSummary {
  id: string;
  status: RequestStatus;
  version: number;
  employee_id: string;
  manager_id: string;
  vendor: string | null;
  total_amount: string | null;
  submitted_at: string;
  updated_at: string;
  latest_required_action: string | null;
}

export interface RequestDetail extends RequestSummary {
  current_owner_role: "employee" | "manager" | "finance" | null;
  manager_decision: {
    decision: "approved" | "rejected";
    decided_by_user_id: string;
    decided_at: string;
    rejection_reason: string | null;
  } | null;
  processing: {
    payment_status: "paid" | "scheduled";
    processed_by_user_id: string;
    processed_at: string;
  } | null;
  document: DocumentView | null;
  extracted_data: ExtractionView | null;
}

export interface CorrectionResult {
  request_id: string;
  version: number;
  extracted_data: ExtractionView;
}

export interface CorrectionPayload {
  expected_version: number;
  vendor: string | null;
  total_amount: string | null;
  invoice_date: string | null;
  line_items: Array<{ description: string; amount: string }>;
}
