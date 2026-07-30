"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import type { AuditEventView } from "@/lib/contracts/audit";
import type {
  CorrectionPayload,
  ExtractionView,
  RequestDetail,
} from "@/lib/contracts/requests";
import {
  correctExtraction,
  getExtractedData,
  getRequest,
  replaceDocument,
  resubmitRequest,
  retryExtraction,
} from "@/lib/api/requests";
import { getAudit } from "@/lib/api/audit";
import { ApiError } from "@/lib/contracts/api-error";
import { LoadingState, PageState } from "@/components/feedback/page-state";
import { RequestDetailCard } from "@/components/requests/request-detail-card";
import { ExtractionDataCard } from "@/components/requests/extraction-data-card";
import { ExtractionCorrectionForm } from "@/components/requests/extraction-correction-form";
import { ExtractionStatusPoller } from "@/components/requests/extraction-status-poller";
import { DocumentActions } from "@/components/documents/document-actions";
import { FileUploadDropzone } from "@/components/documents/file-upload-dropzone";
import { AuditTimeline } from "@/components/audit/audit-timeline";
import { ValidationBanner } from "@/components/feedback/validation-banner";

export default function EmployeeRequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [request, setRequest] = useState<RequestDetail | null>(null);
  const [extraction, setExtraction] = useState<ExtractionView | null>(null);
  const [audit, setAudit] = useState<AuditEventView[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [detail, extracted, history] = await Promise.all([
        getRequest(id),
        getExtractedData(id),
        getAudit(id),
      ]);
      setRequest(detail);
      setExtraction(extracted);
      setAudit(history.items);
    } catch (caught) {
      setError(caught as ApiError);
    }
  }, [id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const poll = useCallback(async () => {
    try {
      const latest = await getExtractedData(id);
      setExtraction(latest);
      if (latest.status !== "pending") await load();
    } catch {
      // The normal page error path handles persistent failures on the next load.
    }
  }, [id, load]);

  async function mutate(action: () => Promise<unknown>) {
    setBusy(true);
    setActionError(null);
    try {
      await action();
      await load();
    } catch (caught) {
      const apiError = caught as ApiError;
      setActionError(apiError);
      if (apiError.code === "VERSION_CONFLICT") await load();
    } finally {
      setBusy(false);
    }
  }

  if (!request && !error) return <LoadingState label="Loading request…" />;
  if (error) {
    return (
      <PageState
        title={error.status === 404 ? "Request not found" : "Unable to load request"}
        message={error.message}
        action={
          <button className="button" onClick={() => void load()}>
            Try again
          </button>
        }
      />
    );
  }
  if (!request || !extraction) return null;

  const editable =
    request.status === "employee_review" || request.status === "rejected";

  return (
    <>
      <Link className="back-link" href="/employee/requests">
        ← Back to requests
      </Link>
      <RequestDetailCard request={request} />
      <ExtractionStatusPoller
        active={extraction.status === "pending"}
        onPoll={poll}
      />
      {actionError && (
        <ValidationBanner
          flags={extraction.validation_flags}
          message={`${actionError.message}${
            actionError.code === "VERSION_CONFLICT"
              ? " The latest request data has been reloaded."
              : ""
          }`}
        />
      )}
      {editable ? (
        <ExtractionCorrectionForm
          key={`${request.id}-${request.version}`}
          busy={busy}
          extraction={extraction}
          onResubmit={() =>
            mutate(() => resubmitRequest(id, request.version))
          }
          onSave={(payload: CorrectionPayload) =>
            mutate(() => correctExtraction(id, payload))
          }
          version={request.version}
        />
      ) : (
        <ExtractionDataCard extraction={extraction} />
      )}
      {extraction.status === "failed" && (
        <section className="card action-card">
          <div>
            <h2>Extraction could not finish</h2>
            <p>Retry the same document when you’re ready.</p>
          </div>
          <button
            className="button primary"
            disabled={busy}
            onClick={() =>
              void mutate(() => retryExtraction(id, request.version))
            }
          >
            {busy ? "Starting retry…" : "Retry extraction"}
          </button>
        </section>
      )}
      {request.document && <DocumentActions document={request.document} />}
      {editable && (
        <section className="card">
          <div className="section-heading">
            <div>
              <span className="eyebrow">Document correction</span>
              <h2>Replace the invoice file</h2>
            </div>
          </div>
          <p className="muted">
            Replacing the file starts a fresh extraction attempt and keeps the
            previous document in history.
          </p>
          <FileUploadDropzone
            busy={busy}
            compact
            onUpload={(file) =>
              mutate(() =>
                replaceDocument(
                  id,
                  request.version,
                  file,
                  setUploadProgress,
                ),
              )
            }
            progress={uploadProgress}
          />
        </section>
      )}
      <AuditTimeline events={audit} />
    </>
  );
}
