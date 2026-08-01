"use client";

import { useState } from "react";
import { Check, LoaderCircle, X } from "lucide-react";

type DecisionMode = "approve" | "reject";

export function ManagerDecisionDialog({
  version,
  busy,
  onApprove,
  onReject,
}: {
  version: number;
  busy: boolean;
  onApprove: () => Promise<boolean>;
  onReject: (reason: string) => Promise<boolean>;
}) {
  const [mode, setMode] = useState<DecisionMode | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");

  function close() {
    if (busy) return;
    setMode(null);
    setReason("");
    setReasonError("");
  }

  async function submit() {
    if (mode === "approve") {
      if (await onApprove()) close();
      return;
    }
    const normalizedReason = reason.trim();
    if (!normalizedReason) {
      setReasonError("Enter a rejection reason.");
      return;
    }
    if (await onReject(normalizedReason)) close();
  }

  return (
    <>
      <section className="card decision-card">
        <div>
          <span className="eyebrow">Manager decision</span>
          <h2>Review this invoice</h2>
          <p>
            Approving sends it to Finance. Rejecting returns it to the
            Employee with your reason.
          </p>
          <small>Current request version: {version}</small>
        </div>
        <div className="button-row">
          <button
            className="button danger"
            disabled={busy}
            onClick={() => setMode("reject")}
            type="button"
          >
            <X aria-hidden="true" size={16} />
            Reject
          </button>
          <button
            className="button primary"
            disabled={busy}
            onClick={() => setMode("approve")}
            type="button"
          >
            <Check aria-hidden="true" size={16} />
            Approve
          </button>
        </div>
      </section>

      {mode && (
        <div
          aria-labelledby="decision-title"
          aria-modal="true"
          className="dialog-backdrop"
          role="dialog"
        >
          <div className="dialog-panel">
            <span className="eyebrow">Confirm decision</span>
            <h2 id="decision-title">
              {mode === "approve" ? "Approve this invoice?" : "Reject this invoice?"}
            </h2>
            <p>
              {mode === "approve"
                ? "This request will move to the Finance review queue."
                : "The Employee will see your reason and can correct and resubmit."}
            </p>
            {mode === "reject" && (
              <label>
                Rejection reason
                <textarea
                  aria-describedby={reasonError ? "reason-error" : undefined}
                  aria-invalid={Boolean(reasonError)}
                  autoFocus
                  onChange={(event) => {
                    setReason(event.target.value);
                    if (reasonError) setReasonError("");
                  }}
                  rows={4}
                  value={reason}
                />
              </label>
            )}
            {reasonError && (
              <span className="field-error" id="reason-error">
                {reasonError}
              </span>
            )}
            <div className="dialog-actions">
              <button
                className="button"
                disabled={busy}
                onClick={close}
                type="button"
              >
                Cancel
              </button>
              <button
                className={mode === "approve" ? "button primary" : "button danger"}
                disabled={busy}
                onClick={() => void submit()}
                type="button"
              >
                {busy && (
                  <LoaderCircle
                    aria-hidden="true"
                    className="spin-icon"
                    size={16}
                  />
                )}
                {busy
                  ? "Saving…"
                  : mode === "approve"
                    ? "Confirm approval"
                    : "Confirm rejection"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
