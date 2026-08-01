"use client";

import { useState } from "react";
import { CheckCircle2, LoaderCircle } from "lucide-react";

export type PaymentStatus = "paid" | "scheduled";

export function FinanceProcessDialog({
  version,
  busy,
  onProcess,
}: {
  version: number;
  busy: boolean;
  onProcess: (paymentStatus: PaymentStatus) => Promise<boolean>;
}) {
  const [open, setOpen] = useState(false);
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus | "">("");
  const [fieldError, setFieldError] = useState("");

  function close() {
    if (busy) return;
    setOpen(false);
    setPaymentStatus("");
    setFieldError("");
  }

  async function submit() {
    if (!paymentStatus) {
      setFieldError("Choose paid or scheduled.");
      return;
    }
    if (await onProcess(paymentStatus)) close();
  }

  return (
    <>
      <section className="card decision-card">
        <div>
          <span className="eyebrow">Finance processing</span>
          <h2>Complete this invoice</h2>
          <p>
            Record whether payment has been completed or scheduled. The actor
            and processing time are recorded by the server.
          </p>
          <small>Current request version: {version}</small>
        </div>
        <button
          className="button primary"
          disabled={busy}
          onClick={() => setOpen(true)}
          type="button"
        >
          <CheckCircle2 aria-hidden="true" size={17} />
          Mark processed
        </button>
      </section>

      {open && (
        <div
          aria-labelledby="process-title"
          aria-modal="true"
          className="dialog-backdrop"
          role="dialog"
        >
          <div className="dialog-panel">
            <span className="eyebrow">Confirm processing</span>
            <h2 id="process-title">How will this invoice be paid?</h2>
            <p>
              This choice is final for the MVP and moves the request to
              processed.
            </p>
            <fieldset className="payment-options">
              <legend>Payment status</legend>
              {(["paid", "scheduled"] as const).map((value) => (
                <label
                  className={paymentStatus === value ? "selected" : ""}
                  key={value}
                >
                  <input
                    checked={paymentStatus === value}
                    name="payment-status"
                    onChange={() => {
                      setPaymentStatus(value);
                      setFieldError("");
                    }}
                    type="radio"
                    value={value}
                  />
                  <span>
                    <strong>{value === "paid" ? "Paid" : "Scheduled"}</strong>
                    <small>
                      {value === "paid"
                        ? "Payment is complete."
                        : "Payment is arranged for a future date."}
                    </small>
                  </span>
                </label>
              ))}
            </fieldset>
            {fieldError && (
              <span className="field-error" role="alert">
                {fieldError}
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
                className="button primary"
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
                {busy ? "Saving…" : "Confirm processing"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
