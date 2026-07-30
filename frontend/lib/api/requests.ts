import { apiRequest, uploadMultipart } from "./browser-client";
import type {
  CorrectionPayload,
  CorrectionResult,
  ExtractionView,
  RequestDetail,
  RequestSummary,
} from "@/lib/contracts/requests";

export const getRequest = (id: string) =>
  apiRequest<RequestDetail>(`/api/v1/requests/${id}`);

export const getExtractedData = (id: string) =>
  apiRequest<ExtractionView>(`/api/v1/requests/${id}/extracted-data`);

export const correctExtraction = (id: string, body: CorrectionPayload) =>
  apiRequest<CorrectionResult>(`/api/v1/requests/${id}/extracted-data`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });

export const resubmitRequest = (id: string, expectedVersion: number) =>
  apiRequest<RequestDetail>(`/api/v1/requests/${id}/resubmit`, {
    method: "POST",
    body: JSON.stringify({ expected_version: expectedVersion }),
  });

export const retryExtraction = (id: string, expectedVersion: number) =>
  apiRequest<RequestSummary>(`/api/v1/requests/${id}/extraction/retry`, {
    method: "POST",
    body: JSON.stringify({ expected_version: expectedVersion }),
  });

export function createRequest(file: File, onProgress?: (value: number) => void) {
  const formData = new FormData();
  formData.append("file", file);
  return uploadMultipart<RequestSummary>({
    path: "/api/v1/requests",
    formData,
    onProgress,
  });
}

export function replaceDocument(
  requestId: string,
  expectedVersion: number,
  file: File,
  onProgress?: (value: number) => void,
) {
  const formData = new FormData();
  formData.append("expected_version", String(expectedVersion));
  formData.append("file", file);
  return uploadMultipart<RequestSummary>({
    path: `/api/v1/requests/${requestId}/documents`,
    formData,
    onProgress,
  });
}
