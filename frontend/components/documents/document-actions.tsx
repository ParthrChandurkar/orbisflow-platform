"use client";

import { useState } from "react";
import type { DocumentView } from "@/lib/contracts/requests";
import { getDocumentAccessLink } from "@/lib/api/documents";
import { absoluteApiUrl } from "@/lib/api/browser-client";
import { ApiError } from "@/lib/contracts/api-error";
import { ExternalLink, LoaderCircle } from "lucide-react";

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
        {busy ? (
          <>
            <LoaderCircle aria-hidden="true" className="spin-icon" size={16} />
            Preparing secure link…
          </>
        ) : (
          <>
            View document
            <ExternalLink aria-hidden="true" size={16} />
          </>
        )}
      </button>
      {error && <div className="alert error">{error}</div>}
    </section>
  );
}
