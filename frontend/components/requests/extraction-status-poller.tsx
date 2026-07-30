"use client";

import { useEffect } from "react";

export function ExtractionStatusPoller({
  active,
  onPoll,
}: {
  active: boolean;
  onPoll: () => void | Promise<void>;
}) {
  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") void onPoll();
    }, 3000);
    return () => window.clearInterval(timer);
  }, [active, onPoll]);
  return active ? (
    <div className="polling-note">
      <span className="spinner small" />
      Extraction is running. This page refreshes every 3 seconds.
    </div>
  ) : null;
}
