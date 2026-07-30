import type { AuditEventView } from "@/lib/contracts/audit";
import { formatDate } from "@/components/dashboards/request-summary-table";

export function AuditTimeline({ events }: { events: AuditEventView[] }) {
  return (
    <section className="card">
      <div className="section-heading">
        <div>
          <span className="eyebrow">Immutable history</span>
          <h2>Audit timeline</h2>
        </div>
      </div>
      {events.length === 0 ? (
        <p className="muted">No audit events recorded yet.</p>
      ) : (
        <ol className="timeline">
          {events.map((event) => (
            <li key={event.id}>
              <span className="timeline-dot" />
              <div>
                <strong>{humanize(event.event_type)}</strong>
                <p>
                  {event.previous_status && event.resulting_status
                    ? `${humanize(event.previous_status)} → ${humanize(event.resulting_status)}`
                    : "Request event recorded"}
                </p>
                <small>
                  {formatDate(event.created_at)} · {event.actor_kind}
                </small>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function humanize(value: string): string {
  return value.replaceAll("_", " ");
}
