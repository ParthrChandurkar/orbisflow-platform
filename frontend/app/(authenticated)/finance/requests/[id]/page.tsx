"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import {
  getExtractedData,
  getRequest,
  processRequest,
} from "@/lib/api/requests";
import { getAudit } from "@/lib/api/audit";
import type { AuditEventView } from "@/lib/contracts/audit";
import type { ExtractionView, RequestDetail } from "@/lib/contracts/requests";
import { ApiError } from "@/lib/contracts/api-error";
import { RequestDetailCard } from "@/components/requests/request-detail-card";
import { ExtractionDataCard } from "@/components/requests/extraction-data-card";
import {
  FinanceProcessDialog,
  type PaymentStatus,
} from "@/components/requests/finance-process-dialog";
import { DocumentActions } from "@/components/documents/document-actions";
import { AuditTimeline } from "@/components/audit/audit-timeline";
import { LoadingState, PageState } from "@/components/feedback/page-state";

export default function FinanceRequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [request, setRequest] = useState<RequestDetail | null>(null);
  const [extraction, setExtraction] = useState<ExtractionView | null>(null);
  const [audit, setAudit] = useState<AuditEventView[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

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

  async function process(paymentStatus: PaymentStatus): Promise<boolean> {
    setBusy(true);
    setActionError(null);
    setNotice("");
    try {
      await processRequest(id, request!.version, paymentStatus);
      await load();
      setNotice(
        paymentStatus === "paid"
          ? "Invoice marked as paid."
          : "Invoice payment scheduled.",
      );
      return true;
    } catch (caught) {
      const apiError = caught as ApiError;
      setActionError(apiError);
      if (
        apiError.code === "VERSION_CONFLICT" ||
        apiError.code === "STATE_CONFLICT"
      ) {
        await load();
      }
      return false;
    } finally {
      setBusy(false);
    }
  }

  if (!request && !error) return <LoadingState label="Loading request…" />;
  if (error) {
    return (
      <PageState
        title={
          error.status === 404
            ? "Request unavailable"
            : "Unable to load request"
        }
        message={
          error.status === 404
            ? "This request has not reached Finance review or is outside Finance scope."
            : error.message
        }
        action={
          <Link className="button" href="/finance/queue">
            Return to Finance queue
          </Link>
        }
      />
    );
  }
  if (!request || !extraction) return null;

  return (
    <>
      <Link className="back-link" href="/finance/queue">
        ← Back to Finance queue
      </Link>
      <RequestDetailCard request={request} />
      {notice && (
        <div className="alert success" role="status">
          {notice}
        </div>
      )}
      {actionError && (
        <div className="alert error" role="alert">
          <strong>
            {actionError.code === "VERSION_CONFLICT"
              ? "This request changed before processing was saved."
              : actionError.code === "STATE_CONFLICT"
                ? "This request has already been processed."
                : "Processing could not be saved."}
          </strong>
          <span>
            {actionError.message}
            {(actionError.code === "VERSION_CONFLICT" ||
              actionError.code === "STATE_CONFLICT") &&
              " The latest request state is shown below."}
          </span>
        </div>
      )}
      <ExtractionDataCard extraction={extraction} />
      {request.status === "finance_review" && (
        <FinanceProcessDialog
          busy={busy}
          onProcess={process}
          version={request.version}
        />
      )}
      {request.document && <DocumentActions document={request.document} />}
      <AuditTimeline events={audit} />
    </>
  );
}
