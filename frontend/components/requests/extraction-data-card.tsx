import type { ExtractionView } from "@/lib/contracts/requests";
import { formatAmount } from "@/components/dashboards/request-summary-table";
import { LineItemsTable } from "./line-items-table";
import { ValidationBanner } from "@/components/feedback/validation-banner";

export function ExtractionDataCard({ extraction }: { extraction: ExtractionView }) {
  return (
    <section className="card">
      <div className="section-heading">
        <div>
          <span className="eyebrow">AI extraction</span>
          <h2>Invoice details</h2>
        </div>
        <span className={`extraction-state ${extraction.status}`}>
          {extraction.status}
        </span>
      </div>
      {extraction.status === "failed" && (
        <ValidationBanner
          message={`Extraction failed: ${humanize(extraction.failure_category ?? "unknown error")}.`}
        />
      )}
      <ValidationBanner flags={extraction.validation_flags} />
      <dl className="detail-grid three">
        <div>
          <dt>Vendor</dt>
          <dd>{extraction.vendor ?? "Missing"}</dd>
        </div>
        <div>
          <dt>Total amount</dt>
          <dd>{formatAmount(extraction.total_amount)}</dd>
        </div>
        <div>
          <dt>Invoice date</dt>
          <dd>{extraction.invoice_date ?? "Missing"}</dd>
        </div>
      </dl>
      <h3>Line items</h3>
      <LineItemsTable items={extraction.line_items} />
    </section>
  );
}

function humanize(value: string): string {
  return value.replaceAll("_", " ");
}
