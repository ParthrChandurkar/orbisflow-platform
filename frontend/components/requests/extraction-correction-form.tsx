"use client";

import { FormEvent, useState } from "react";
import type {
  CorrectionPayload,
  ExtractionView,
} from "@/lib/contracts/requests";

interface EditableItem {
  description: string;
  amount: string;
}

export function ExtractionCorrectionForm({
  extraction,
  version,
  busy,
  onSave,
  onResubmit,
}: {
  extraction: ExtractionView;
  version: number;
  busy: boolean;
  onSave: (payload: CorrectionPayload) => Promise<void>;
  onResubmit: () => Promise<void>;
}) {
  const [vendor, setVendor] = useState(extraction.vendor ?? "");
  const [total, setTotal] = useState(extraction.total_amount ?? "");
  const [date, setDate] = useState(extraction.invoice_date ?? "");
  const [items, setItems] = useState<EditableItem[]>(
    extraction.line_items.map(({ description, amount }) => ({
      description,
      amount,
    })),
  );

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSave({
      expected_version: version,
      vendor: vendor.trim() || null,
      total_amount: total || null,
      invoice_date: date || null,
      line_items: items,
    });
  }

  function changeItem(
    index: number,
    field: keyof EditableItem,
    value: string,
  ) {
    setItems((current) =>
      current.map((item, itemIndex) =>
        itemIndex === index ? { ...item, [field]: value } : item,
      ),
    );
  }

  return (
    <section className="card">
      <div className="section-heading">
        <div>
          <span className="eyebrow">Employee action</span>
          <h2>Correct invoice details</h2>
        </div>
      </div>
      <form className="correction-form" onSubmit={submit}>
        <div className="form-grid">
          <label>
            Vendor
            <input value={vendor} onChange={(e) => setVendor(e.target.value)} />
          </label>
          <label>
            Total amount
            <input
              inputMode="decimal"
              value={total}
              onChange={(e) => setTotal(e.target.value)}
            />
          </label>
          <label>
            Invoice date
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </label>
        </div>
        <div className="line-editor">
          <div className="section-heading">
            <h3>Line items</h3>
            <button
              className="text-button"
              onClick={() =>
                setItems((current) => [
                  ...current,
                  { description: "", amount: "" },
                ])
              }
              type="button"
            >
              + Add line
            </button>
          </div>
          {items.length === 0 && (
            <p className="muted">No line items. The total can still be submitted.</p>
          )}
          {items.map((item, index) => (
            <div className="line-edit-row" key={index}>
              <input
                aria-label={`Line ${index + 1} description`}
                placeholder="Description"
                required
                value={item.description}
                onChange={(e) =>
                  changeItem(index, "description", e.target.value)
                }
              />
              <input
                aria-label={`Line ${index + 1} amount`}
                inputMode="decimal"
                placeholder="0.00"
                required
                value={item.amount}
                onChange={(e) => changeItem(index, "amount", e.target.value)}
              />
              <button
                aria-label={`Remove line ${index + 1}`}
                className="icon-button"
                onClick={() =>
                  setItems((current) =>
                    current.filter((_, itemIndex) => itemIndex !== index),
                  )
                }
                type="button"
              >
                ×
              </button>
            </div>
          ))}
        </div>
        <div className="button-row">
          <button className="button" disabled={busy} type="submit">
            {busy ? "Saving…" : "Save corrections"}
          </button>
          <button
            className="button primary"
            disabled={busy}
            onClick={() => void onResubmit()}
            type="button"
          >
            Resubmit for approval
          </button>
        </div>
      </form>
    </section>
  );
}
