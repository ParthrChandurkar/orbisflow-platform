import { CircleAlert, Inbox } from "lucide-react";

export function PageState({
  title,
  message,
  action,
}: {
  title: string;
  message?: string;
  action?: React.ReactNode;
}) {
  const errorState = /unable|couldn|not found|unavailable/i.test(title);
  const Icon = errorState ? CircleAlert : Inbox;
  return (
    <section className="page-state">
      <div className={errorState ? "state-icon error" : "state-icon"}>
        <Icon aria-hidden="true" size={24} strokeWidth={1.8} />
      </div>
      <h2>{title}</h2>
      {message && <p>{message}</p>}
      {action}
    </section>
  );
}

export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="table-skeleton" aria-busy="true" aria-live="polite">
      <span className="sr-only">{label}</span>
      <div className="skeleton skeleton-toolbar" />
      {[0, 1, 2, 3].map((row) => (
        <div className="skeleton-row" key={row}>
          <div className="skeleton skeleton-cell wide" />
          <div className="skeleton skeleton-pill" />
          <div className="skeleton skeleton-cell" />
          <div className="skeleton skeleton-cell" />
        </div>
      ))}
    </div>
  );
}

export function DetailSkeleton({ label = "Loading request…" }: { label?: string }) {
  return (
    <div className="detail-skeleton" aria-busy="true" aria-live="polite">
      <span className="sr-only">{label}</span>
      <div className="card skeleton-detail-card">
        <div className="skeleton skeleton-kicker" />
        <div className="skeleton skeleton-title" />
        <div className="skeleton skeleton-copy" />
        <div className="skeleton-metric-grid">
          {[0, 1, 2, 3].map((item) => (
            <div className="skeleton skeleton-metric" key={item} />
          ))}
        </div>
      </div>
      <div className="card skeleton-content-card">
        <div className="skeleton skeleton-kicker" />
        <div className="skeleton skeleton-title short" />
        <div className="skeleton skeleton-block" />
      </div>
    </div>
  );
}

export function NotificationSkeleton({
  label = "Loading notifications…",
}: {
  label?: string;
}) {
  return (
    <div className="notification-skeleton" aria-busy="true" aria-live="polite">
      <span className="sr-only">{label}</span>
      {[0, 1, 2, 3].map((item) => (
        <div className="skeleton-notification" key={item}>
          <div className="skeleton skeleton-avatar" />
          <div>
            <div className="skeleton skeleton-copy" />
            <div className="skeleton skeleton-copy short" />
          </div>
        </div>
      ))}
    </div>
  );
}
