export function PageState({
  title,
  message,
  action,
}: {
  title: string;
  message?: string;
  action?: React.ReactNode;
}) {
  return (
    <section className="page-state">
      <div className="state-icon">○</div>
      <h2>{title}</h2>
      {message && <p>{message}</p>}
      {action}
    </section>
  );
}

export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="loading-state" aria-live="polite">
      <div className="spinner" />
      <span>{label}</span>
    </div>
  );
}
