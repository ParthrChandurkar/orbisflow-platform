"use client";

import { useState } from "react";
import type { DocumentView } from "@/lib/contracts/requests";
import { getDocumentAccessLink } from "@/lib/api/documents";
import { absoluteApiUrl } from "@/lib/api/browser-client";
import { ApiError } from "@/lib/contracts/api-error";

export function DocumentActions({ document }: { document: DocumentView }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function openDocument() {
    setBusy(true);
    setError("");
    const tab = window.open("", "_blank");
    try {
      const link = await getDocumentAccessLink(document.id);
      if (tab) {
        tab.opener = null;
        tab.location.href = absoluteApiUrl(link.url);
      } else {
        window.location.assign(absoluteApiUrl(link.url));
      }
    } catch (caught) {
      tab?.close();
      setError(
        caught instanceof ApiError ? caught.message : "Unable to open document.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card document-card">
      <div>
        <span className="eyebrow">Current document</span>
        <h2>{document.original_filename}</h2>
        <p>
          {document.mime_type} · {(document.file_size_bytes / 1024).toFixed(1)} KB
        </p>
      </div>
      <button className="button" disabled={busy} onClick={openDocument}>
        {busy ? "Preparing secure link…" : "View document"}
      </button>
      {error && <div className="alert error">{error}</div>}
    </section>
  );
}
