export type { PageResponse, RequestSummary } from "./requests";

export interface TeamActivity {
  pending: number;
  approved: number;
  rejected: number;
}
