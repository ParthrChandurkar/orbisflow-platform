import type { ValidationFlag } from "@/lib/contracts/requests";

export function ValidationBanner({
  flags = [],
  message,
}: {
  flags?: ValidationFlag[];
  message?: string;
}) {
  if (flags.length === 0 && !message) return null;
  return (
    <div className="alert warning">
      <strong>Invoice details need attention</strong>
      {message && <span>{message}</span>}
      {flags.length > 0 && (
        <ul>
          {flags.map((flag) => (
            <li key={`${flag.code}-${flag.field}`}>{flag.message}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
