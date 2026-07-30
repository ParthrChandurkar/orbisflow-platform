import type { TeamActivity } from "@/lib/contracts/dashboards";

const cards = [
  {
    key: "pending",
    label: "Pending review",
    detail: "Waiting for your decision",
  },
  {
    key: "approved",
    label: "Approved",
    detail: "Sent to or processed by Finance",
  },
  {
    key: "rejected",
    label: "Rejected",
    detail: "Returned for Employee correction",
  },
] as const;

export function TeamActivityCards({
  activity,
}: {
  activity: TeamActivity;
}) {
  return (
    <section aria-label="Team activity" className="activity-grid">
      {cards.map((card) => (
        <article className={`activity-card ${card.key}`} key={card.key}>
          <span>{card.label}</span>
          <strong>{activity[card.key]}</strong>
          <small>{card.detail}</small>
        </article>
      ))}
    </section>
  );
}
